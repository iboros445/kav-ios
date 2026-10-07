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
}
