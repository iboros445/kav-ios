// Activity result contracts for the cases Kav uses. `launch` is async on iOS exactly as it is on Android: the result
// arrives in the callback. Permission requests map onto iOS authorisation (androidx.core.content.Permissions);
// document pickers are UIDocumentPickerViewController (android.content.DocumentPickers).
package androidx.activity.result.contract

import android.content.DocumentPickers
import android.net.Uri
import androidx.core.content.Permissions

abstract class ActivityResultContract<I, O> {
    abstract fun run(input: I, done: (O) -> Unit)
}

object ActivityResultContracts {
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun run(input: String, done: (Boolean) -> Unit) {
            Permissions.request(arrayOf(input)) { done(it[input] == true) }
        }
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun run(input: Array<String>, done: (Map<String, Boolean>) -> Unit) {
            Permissions.request(input, done)
        }
    }

    /** The result is a Uri to write to. iOS asks where to save after the file is written, so the file is staged first. */
    class CreateDocument(private val mimeType: String) : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) = DocumentPickers.stageForSave(input, mimeType, done)
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(input: Array<String>, done: (Uri?) -> Unit) = DocumentPickers.open(done)
    }

    /** Photos from the library (PHPickerViewController), copied into the cache so they read as file Uris. */
    class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>() {
        override fun run(input: androidx.activity.result.PickVisualMediaRequest, done: (Uri?) -> Unit) =
            android.content.PhotoPicker.pick(1) { done(it.firstOrNull()) }

        interface VisualMediaType
        object ImageOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        object VideoOnly : VisualMediaType
    }

    class PickMultipleVisualMedia(private val maxItems: Int = 20) : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, List<Uri>>() {
        override fun run(input: androidx.activity.result.PickVisualMediaRequest, done: (List<Uri>) -> Unit) =
            android.content.PhotoPicker.pick(maxItems, done)
    }

    /** Android hands an Intent to another app and gets its answer; iOS has none to hand it to, so it comes back cancelled. */
    class StartActivityForResult : ActivityResultContract<android.content.Intent, androidx.activity.result.ActivityResult>() {
        override fun run(input: android.content.Intent, done: (androidx.activity.result.ActivityResult) -> Unit) =
            done(androidx.activity.result.ActivityResult(android.app.Activity.RESULT_CANCELED, null))
    }
}
