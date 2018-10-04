package tk.hacker1024.epimetheus.fragments

import androidx.fragment.app.Fragment

//import android.annotation.SuppressLint
//import android.graphics.Bitmap
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.webkit.*
//import androidx.fragment.app.Fragment
//import kotlinx.android.synthetic.main.fragment_register.view.*
//import okhttp3.Request
//import tk.superl2.epimetheus.MainActivity
//import tk.superl2.epimetheus.R
//import tk.superl2.epimetheus.dialogs.showNetworkErrorDialog
//import java.io.InputStream
//import java.net.SocketException
//import java.net.SocketTimeoutException
//
////TODO fix this page!

class RegisterFragment : Fragment()

//class RegisterFragment : Fragment() {
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        return inflater.inflate(R.layout.fragment_register, container, false)
//    }
//
//    @SuppressLint("SetJavaScriptEnabled")
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        view.register_webview.apply {
//            webViewClient = PandoraWebViewClient()
//            settings.domStorageEnabled = true
//            settings.javaScriptEnabled = true
//            settings.cacheMode = WebSettings.LOAD_NO_CACHE
////            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64:64.0) Gecko/20100101 Firefox/64.0"
//            loadUrl("https://www.pandora.com/subscription/register")
//        }
//    }
//
//    fun networkError() {
//        requireActivity().runOnUiThread { //TODO dialog show too many times
//            showNetworkErrorDialog(context!!,
//                ok = { dialog, _ ->
//                    dialog.dismiss()
//                    (activity as MainActivity?)?.onSupportNavigateUp()
//                },
//                exit = { dialog, _ ->
//                    dialog.dismiss()
//                    (activity as MainActivity?)?.finishAndRemoveTask()
//                }
//            )
//        }
//    }
//
//    inner class PandoraWebViewClient : WebViewClient() {
//        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
//            return if (request.url.toString().contains("pandora") && request.url.toString().contains("method")) {
//                println(request.url.toString())
//                PandoraWebResourceResponse(
//                    request.url.toString(),
//                    request.requestHeaders["User-Agent"]!!
//                )
//            } else super.shouldInterceptRequest(view, request)
//        }
//
//        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
//            url.removePrefix("https://").removePrefix("www.").apply {
//                if (
//                    this != "pandora.com/account/register" &&
//                    this != "pandora.com/subscription/register" &&
//                    this != "pandora.com/restricted" &&
//                    this != "pandora.com/legal" &&
//                    this != "pandora.com/privacy"
//                ) (activity as MainActivity?)?.onSupportNavigateUp()
//            }
//        }
//
//        override fun onPageFinished(view: WebView, url: String) {
//            this@RegisterFragment.view?.register_progress_bar?.visibility = View.GONE
//            view.visibility = View.VISIBLE
//        }
//    }
//
//    inner class PandoraWebResourceResponse private constructor(mimeType: String, encoding: String, data: InputStream?) : WebResourceResponse(mimeType, encoding, data) {
//        private lateinit var url: String
//        private lateinit var userAgent: String
//
//        constructor(url: String, userAgent: String) : this("", "", null) {
//            this.url = url
//            this.userAgent = userAgent
//        }
//
//        override fun getMimeType(): String? =
//            try {
//                client.newCall(Request.Builder().head().url(url).build()).execute().header("Content-Type")!!.removeSuffix(";charset=utf-8")
//            } catch (exception: Exception) {
//                when (exception) {
//                    is SocketException, is SocketTimeoutException -> networkError()
//                    else -> throw exception
//                }
//                null
//            }
//
//
//        override fun getEncoding(): String? =
//            try {
//                client.newCall(Request.Builder().head().url(url).build()).execute().header("Content-Encoding", "text/plain")!!
//            } catch (exception: Exception) {
//                when (exception) {
//                    is SocketException, is SocketTimeoutException -> networkError()
//                    else -> throw exception
//                }
//                null
//            }
//
//        override fun getData(): InputStream? =
//            try {
//                client.newCall(
//                    Request.Builder()
//                        .header("User-Agent", userAgent)
//                        .url(url)
//                        .build()
//                ).execute().body()!!.byteStream()
//            } catch (exception: Exception) {
//                when (exception) {
//                    is SocketException, is SocketTimeoutException -> networkError()
//                    else -> throw exception
//                }
//                null
//            }
//
//        override fun getResponseHeaders(): MutableMap<String, String>? {
//            return mutableMapOf<String, String>().apply {
//                val map = client.newCall(Request.Builder().head().url(url).build()).execute().headers().toMultimap()
//                for (i in 0 until map.size) {
//                    put(map.keys.elementAt(i), map.values.elementAt(i)[0])
//                }
//                put("Access-Control-Allow-Origin", "*")
//            }
//        }
//    }
//}
