package tk.hacker1024.epimetheus

import com.bumptech.glide.load.engine.GlideException
import tk.hacker1024.libepimetheus.PandoraException
import java.io.IOException

inline fun tryNetworkOperation(onFailure: (exception: Exception) -> Unit = {}, operation: () -> Unit) {
    try {
        operation()
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: PandoraException) {
        onFailure(e)
    } catch (e: GlideException) {
        onFailure(e)
    }
}