package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContracts

class PickVisualMediaRequest(val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo)

class ActivityResult(val resultCode: Int, val data: android.content.Intent?)
