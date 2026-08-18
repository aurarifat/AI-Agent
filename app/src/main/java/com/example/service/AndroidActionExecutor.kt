package com.example.service

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.example.model.ActionType
import com.example.model.AgentAction
import com.example.model.AgentTaskPlan
import com.example.model.AppInfo
import com.example.model.ContactItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

sealed class ExecutionStepStatus {
    data object Pending : ExecutionStepStatus()
    data class Running(val message: String) : ExecutionStepStatus()
    data class Success(val message: String) : ExecutionStepStatus()
    data class Failed(val error: String) : ExecutionStepStatus()
}

data class TimelineStep(
    val id: Int,
    val title: String,
    val description: String,
    val status: ExecutionStepStatus = ExecutionStepStatus.Pending,
    val action: AgentAction? = null
)

class AndroidActionExecutor(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    fun isTaskCancelled(): Boolean = isCancelled.get()

    fun reset() {
        isCancelled.set(false)
    }

    suspend fun executePlan(
        plan: AgentTaskPlan,
        onTimelineUpdated: (List<TimelineStep>) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        reset()

        // Build initial timeline
        val initialSteps = mutableListOf<TimelineStep>()
        initialSteps.add(TimelineStep(0, "Understanding command", plan.task, ExecutionStepStatus.Success("Analyzed")))
        initialSteps.add(TimelineStep(1, "Planning actions", "${plan.actions.size} actions generated", ExecutionStepStatus.Success("Validated")))

        plan.actions.forEachIndexed { index, action ->
            initialSteps.add(
                TimelineStep(
                    id = index + 2,
                    title = action.toReadableDescription(),
                    description = "Pending execution",
                    status = ExecutionStepStatus.Pending,
                    action = action
                )
            )
        }
        initialSteps.add(TimelineStep(plan.actions.size + 2, "Task completion", "Finalize execution", ExecutionStepStatus.Pending))

        var currentSteps = initialSteps.toList()
        onTimelineUpdated(currentSteps)

        var executedCount = 0

        for (index in plan.actions.indices) {
            if (isCancelled.get()) {
                currentSteps = currentSteps.mapIndexed { i, step ->
                    if (i == index + 2) step.copy(status = ExecutionStepStatus.Failed("Task cancelled by user"))
                    else step
                }
                onTimelineUpdated(currentSteps)
                return@withContext Result.failure(CancellationException("Task stopped by user"))
            }

            val stepId = index + 2
            val action = plan.actions[index]

            // Mark step as running
            currentSteps = currentSteps.mapIndexed { i, step ->
                if (i == stepId) step.copy(
                    description = "Executing...",
                    status = ExecutionStepStatus.Running("In progress")
                ) else step
            }
            onTimelineUpdated(currentSteps)

            val stepResult = executeSingleAction(action)

            if (stepResult.isSuccess) {
                executedCount++
                currentSteps = currentSteps.mapIndexed { i, step ->
                    if (i == stepId) step.copy(
                        description = stepResult.getOrDefault("Completed"),
                        status = ExecutionStepStatus.Success(stepResult.getOrDefault("Done"))
                    ) else step
                }
                onTimelineUpdated(currentSteps)
            } else {
                val errorMsg = stepResult.exceptionOrNull()?.message ?: "Action failed"
                currentSteps = currentSteps.mapIndexed { i, step ->
                    if (i == stepId) step.copy(
                        description = errorMsg,
                        status = ExecutionStepStatus.Failed(errorMsg)
                    ) else step
                }
                onTimelineUpdated(currentSteps)
                return@withContext Result.failure(IllegalStateException("Step ${index + 1} failed: $errorMsg"))
            }
        }

        // Final completion step
        val finalIndex = currentSteps.size - 1
        currentSteps = currentSteps.mapIndexed { i, step ->
            if (i == finalIndex) step.copy(
                description = "All $executedCount actions finished successfully",
                status = ExecutionStepStatus.Success("Completed")
            ) else step
        }
        onTimelineUpdated(currentSteps)

        Result.success("Task completed successfully ($executedCount actions executed)")
    }

    private suspend fun executeSingleAction(action: AgentAction): Result<String> {
        return try {
            when (action.type) {
                ActionType.OPEN_APP -> openApp(action.packageName, action.appName)
                ActionType.OPEN_URL -> openUrl(action.url)
                ActionType.WAIT -> {
                    val waitTime = action.milliseconds ?: 1000L
                    var elapsed = 0L
                    while (elapsed < waitTime) {
                        if (isCancelled.get()) throw CancellationException("Stopped during wait")
                        val chunk = minOf(100L, waitTime - elapsed)
                        delay(chunk)
                        elapsed += chunk
                    }
                    Result.success("Waited ${waitTime}ms")
                }
                ActionType.CLICK -> {
                    val target = action.target ?: return Result.failure(IllegalArgumentException("No click target provided"))
                    val service = AgentAccessibilityService.get()
                    if (service != null) {
                        val clicked = service.clickTarget(target)
                        if (clicked) Result.success("Tapped \"$target\"")
                        else Result.failure(IllegalStateException("Element \"$target\" not found on screen"))
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service is not enabled"))
                    }
                }
                ActionType.LONG_CLICK -> {
                    val target = action.target ?: return Result.failure(IllegalArgumentException("No long click target provided"))
                    val service = AgentAccessibilityService.get()
                    if (service != null) {
                        val clicked = service.longClickTarget(target)
                        if (clicked) Result.success("Long pressed \"$target\"")
                        else Result.failure(IllegalStateException("Element \"$target\" not found on screen"))
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service is not enabled"))
                    }
                }
                ActionType.TYPE_TEXT -> {
                    val text = action.text ?: return Result.failure(IllegalArgumentException("No text to type provided"))
                    val service = AgentAccessibilityService.get()
                    if (service != null) {
                        val typed = service.typeText(action.target, text)
                        if (typed) Result.success("Typed \"$text\"")
                        else Result.failure(IllegalStateException("Failed to input text into target field"))
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service is not enabled"))
                    }
                }
                ActionType.SWIPE -> {
                    val service = AgentAccessibilityService.get()
                    if (service != null) {
                        val swiped = service.swipe(action.direction ?: "UP")
                        if (swiped) Result.success("Swiped ${action.direction ?: "UP"}")
                        else Result.failure(IllegalStateException("Swipe gesture failed"))
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service is not enabled"))
                    }
                }
                ActionType.SCROLL -> {
                    val service = AgentAccessibilityService.get()
                    if (service != null) {
                        val scrolled = service.scroll(action.direction ?: "FORWARD")
                        if (scrolled) Result.success("Scrolled ${action.direction ?: "FORWARD"}")
                        else Result.failure(IllegalStateException("Scroll failed"))
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service is not enabled"))
                    }
                }
                ActionType.PRESS_BACK -> {
                    val service = AgentAccessibilityService.get()
                    if (service != null && service.pressBack()) {
                        Result.success("Pressed Back")
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service unavailable for Back action"))
                    }
                }
                ActionType.PRESS_HOME -> {
                    val service = AgentAccessibilityService.get()
                    if (service != null && service.pressHome()) {
                        Result.success("Pressed Home")
                    } else {
                        Result.failure(IllegalStateException("Accessibility Service unavailable for Home action"))
                    }
                }
                ActionType.SEARCH -> performSearch(action.text ?: action.target, action.appName)
                ActionType.FIND_CONTACT -> findContact(action.name)
                ActionType.CALL_CONTACT -> callContact(action.name, action.phoneNumber)
                ActionType.REQUEST_CONFIRMATION -> Result.success("User confirmed action")
                ActionType.STOP_TASK -> Result.success("Task finished")
            }
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openApp(packageName: String?, appName: String?): Result<String> {
        val pm = context.packageManager
        val resolvedPackage = resolvePackage(packageName, appName)

        if (resolvedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(resolvedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return Result.success("Launched ${appName ?: resolvedPackage}")
            }
        }

        // Try direct explicit intent for common system settings / apps
        val explicitIntent = resolveSpecialIntent(appName ?: packageName ?: "")
        if (explicitIntent != null) {
            explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(explicitIntent)
            return Result.success("Opened ${appName ?: packageName}")
        }

        return Result.failure(IllegalStateException("App '${appName ?: packageName}' is not installed on this device"))
    }

    private fun resolvePackage(packageName: String?, appName: String?): String? {
        if (!packageName.isNullOrBlank()) {
            if (isPackageInstalled(packageName)) return packageName
        }

        val nameLower = (appName ?: "").lowercase().trim()
        val commonMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            "chrome" to "com.android.chrome",
            "google chrome" to "com.android.chrome",
            "settings" to "com.android.settings",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "calculator" to "com.google.android.calculator",
            "photos" to "com.google.android.apps.photos",
            "phone" to "com.google.android.dialer",
            "dialer" to "com.google.android.dialer",
            "camera" to "com.android.camera2"
        )

        commonMap[nameLower]?.let {
            if (isPackageInstalled(it)) return it
        }

        // Search installed apps by label
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(nameLower) || nameLower.contains(label)) {
                return app.packageName
            }
        }

        return commonMap[nameLower] // Fallback to standard package name
    }

    private fun resolveSpecialIntent(query: String): Intent? {
        val q = query.lowercase().trim()
        return when {
            q.contains("settings") -> Intent(android.provider.Settings.ACTION_SETTINGS)
            q.contains("wifi") || q.contains("wi-fi") -> Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            q.contains("bluetooth") -> Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            q.contains("display") -> Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
            q.contains("dialer") || q.contains("phone") -> Intent(Intent.ACTION_DIAL)
            else -> null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openUrl(url: String?): Result<String> {
        val targetUrl = url?.trim() ?: return Result.failure(IllegalArgumentException("URL is empty"))
        val uri = Uri.parse(if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) "https://$targetUrl" else targetUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return Result.success("Opened $targetUrl")
    }

    private fun performSearch(query: String?, appName: String?): Result<String> {
        val q = query?.trim() ?: return Result.failure(IllegalArgumentException("Search query is empty"))
        val app = (appName ?: "").lowercase()

        if (app.contains("youtube")) {
            val youtubeIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (isPackageInstalled("com.google.android.youtube")) {
                context.startActivity(youtubeIntent)
                return Result.success("Searched YouTube for \"$q\"")
            }
            // Fallback to web search on YouTube
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            return Result.success("Searched YouTube on web for \"$q\"")
        }

        // Generic web search
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, q)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(searchIntent)
        return Result.success("Searched web for \"$q\"")
    }

    fun findContact(name: String?): Result<String> {
        val queryName = name?.trim() ?: return Result.failure(IllegalArgumentException("Contact name is empty"))
        if (!PermissionHelper.isReadContactsGranted(context)) {
            return Result.failure(IllegalStateException("Read Contacts permission is not granted"))
        }

        val contacts = searchContacts(queryName)
        return if (contacts.isNotEmpty()) {
            val primary = contacts.first()
            Result.success("Found ${primary.name}: ${primary.phoneNumber}")
        } else {
            Result.failure(IllegalStateException("No contact found matching \"$queryName\""))
        }
    }

    fun searchContacts(query: String): List<ContactItem> {
        val results = mutableListOf<ContactItem>()
        if (!PermissionHelper.isReadContactsGranted(context)) return results

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val idCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val id = if (idCol >= 0) it.getString(idCol) ?: "" else ""
                val name = if (nameCol >= 0) it.getString(nameCol) ?: "" else ""
                val number = if (numCol >= 0) it.getString(numCol) ?: "" else ""
                if (number.isNotBlank()) {
                    results.add(ContactItem(id = id, name = name, phoneNumber = number))
                }
            }
        }
        return results
    }

    private fun callContact(name: String?, phoneNumber: String?): Result<String> {
        var numberToCall = phoneNumber?.trim()
        val contactName = name?.trim() ?: "Contact"

        if (numberToCall.isNullOrBlank() && !name.isNullOrBlank()) {
            val found = searchContacts(name)
            if (found.isNotEmpty()) {
                numberToCall = found.first().phoneNumber
            }
        }

        if (numberToCall.isNullOrBlank()) {
            // Fallback to launching dialer
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            return Result.success("Opened Phone Dialer for $contactName")
        }

        return if (PermissionHelper.isCallPhoneGranted(context)) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(numberToCall)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            Result.success("Calling $contactName ($numberToCall)")
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(numberToCall)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            Result.success("Opened dialer for $contactName ($numberToCall)")
        }
    }

    fun getInstalledAppsList(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            if (pkg != context.packageName) {
                AppInfo(appName = label, packageName = pkg)
            } else null
        }.sortedBy { it.appName }
    }
}
