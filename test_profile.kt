// Let's test the MultipartBody syntax
import okhttp3.MultipartBody
val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
builder.addFormDataPart("key", "value")
