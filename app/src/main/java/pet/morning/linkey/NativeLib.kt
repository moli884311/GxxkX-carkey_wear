package pet.morning.linkey

class NativeLib {

    companion object {
        init {
            System.loadLibrary("linkey")
        }
    }

    external fun initAuth(key: ByteArray, random: ByteArray, deviceId: String)
    external fun getNextCommand(): BleCommand?
    external fun feedNotification(data: ByteArray)
    external fun setControlIntent(intent: String)
    external fun destroySession()
    external fun verifySignature(data: ByteArray, signature: ByteArray): Boolean
    external fun getSecureDeviceId(): String
}
