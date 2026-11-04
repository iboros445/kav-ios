// The photo library through PHPickerViewController. Each picked image is copied as JPEG into the cache and handed
// back as a file Uri, which ContentResolver.openInputStream reads like any other.
package android.content

import android.net.Uri
import kav.compat.native.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify

internal object PhotoPicker {
    private var live: Delegate? = null

    fun pick(max: Int, done: (List<Uri>) -> Unit) {
        val config = PHPickerConfiguration().apply {
            selectionLimit = max.toLong()
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration = config)
        val d = Delegate(done)
        live = d
        picker.delegate = d
        top()?.presentViewController(picker, animated = true, completion = null) ?: run { live = null; done(emptyList()) }
    }

    private fun top(): UIViewController? {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }

    private class Delegate(private val done: (List<Uri>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val results = didFinishPicking.filterIsInstance<PHPickerResult>()
            val out = arrayOfNulls<Uri>(results.size)
            val group = dispatch_group_create()
            results.forEachIndexed { i, r ->
                dispatch_group_enter(group)
                // Any image type the library holds (HEIC too), re-encoded as JPEG through UIImage.
                r.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { raw, _ ->
                    raw?.let { UIImage.imageWithData(it) }?.let { UIImageJPEGRepresentation(it, 0.9) }?.let { data: NSData ->
                        val f = java.io.File(KavApplicationContext.cacheDir, "picked-" + NSUUID().UUIDString + ".jpg")
                        runCatching { f.writeBytes(data.toByteArray()) }.onSuccess { out[i] = Uri.fromFile(f) }
                    }
                    dispatch_group_leave(group)
                }
            }
            dispatch_group_notify(group, dispatch_get_main_queue()) {
                live = null
                done(out.filterNotNull())
            }
        }
    }
}
