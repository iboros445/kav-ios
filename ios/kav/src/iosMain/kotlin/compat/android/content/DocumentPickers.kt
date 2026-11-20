package android.content

import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

/**
 * Android's CreateDocument returns a Uri to write into. iOS's save picker needs the finished file, so a temp file is
 * handed out instead, and the save picker opens when the caller closes the stream it wrote through (see
 * ContentResolver.openOutputStream). OpenDocument copies the chosen file into the temp directory and returns that.
 */
internal object DocumentPickers {
    private val staged = HashMap<String, File>()
    private var delegate: NSObject? = null   // the picker keeps only a weak reference

    fun stageForSave(name: String, @Suppress("UNUSED_PARAMETER") mime: String, done: (Uri?) -> Unit) {
        val dir = File(NSTemporaryDirectory(), "export").also { it.mkdirs() }
        val file = File(dir, name)
        file.delete()
        FileOutputStream(file).use { }
        val uri = Uri.fromFile(file)
        staged[uri.toString()] = file
        done(uri)
    }

    /** Called when a staged file has been written: ask where to keep it. */
    fun finishSave(uri: Uri): Boolean {
        val file = staged.remove(uri.toString()) ?: return false
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(file.path)), asCopy = true)
        IntentRouter.topViewController()?.presentViewController(picker, animated = true, completion = null)
        return true
    }

    fun open(done: (Uri?) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem, UTTypeData), asCopy = true)
        val d = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                delegate = null
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                done(url?.path?.let { Uri.fromFile(File(it)) })
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                delegate = null
                done(null)
            }
        }
        delegate = d
        picker.delegate = d
        IntentRouter.topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}
