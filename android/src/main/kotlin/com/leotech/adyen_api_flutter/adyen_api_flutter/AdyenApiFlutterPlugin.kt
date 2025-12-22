package com.leotech.adyen_api_flutter.adyen_api_flutter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import com.adyen.Client
import com.adyen.Config
import com.adyen.Service
import com.adyen.enums.Environment
import com.adyen.httpclient.TerminalLocalAPIHostnameVerifier
import com.adyen.model.applicationinfo.ApplicationInfo
import com.adyen.model.applicationinfo.CommonField
import com.adyen.model.applicationinfo.ExternalPlatform
import com.adyen.model.nexo.*
import com.adyen.model.terminal.SaleToAcquirerData
import com.adyen.model.terminal.TerminalAPIRequest
import com.adyen.model.terminal.TerminalAPIResponse
import com.adyen.model.terminal.security.SecurityKey
import com.adyen.service.TerminalLocalAPI
import com.adyen.service.TerminalLocalAPIUnencrypted
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.InputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File


/** AdyenApiFlutterPlugin */
class AdyenApiFlutterPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel

  // test logging
  val tag = "LOG"

  private var client: Client? = null
  private var service: Service? = null
  private var securityKey: SecurityKey? = null
  private var terminalLocalAPI: TerminalLocalAPI? = null
  private var certificateInputStream: InputStream? = null
  private var sslContext: SSLContext? = null
  private lateinit var context: Context
  private var currentServiceID: String? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "adyen_api_flutter")
    channel.setMethodCallHandler(this)

    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "init" -> {
        init(
          call.argument<String>("ipAddress")!!,
          call.argument<Int>("keyVersion")!!,
          call.argument<String>("keyIdentifier")!!,
          call.argument<String>("keyPassphrase")!!,
          call.argument<Boolean>("testEnvironment")!!,
          result)
      }
      "dispose" -> {
        dispose(result)
      }
      "paymentRequest" -> {
        paymentRequest(
          call.argument<Double>("amount")!!,
          call.argument<String>("POIID")!!,
          call.argument<String>("saleID")!!,
          result
        )
      }
      "refundRequest" -> {
        refundRequest(
          call.argument<String>("transactionID")!!,
          call.argument<String>("POIID")!!,
          call.argument<String>("saleID")!!,
          call.argument<Double>("refundAmount"),
          call.argument<String>("currencyCode") ?: "AUD",
          result
        )
      }
      "statusRequest" -> {
        val statusRequestTypeString = call.argument<String>("statusRequestType")!!
        val statusRequestType = MessageCategoryType.valueOf(statusRequestTypeString)
        statusRequest(
          call.argument<String>("transactionServiceID")!!,
          statusRequestType,
          call.argument<String>("POIID")!!,
          call.argument<String>("saleID")!!,
          result
        )
      }
      "abortRequest" -> {
        abortRequest(
          call.argument<String>("POIID")!!,
          call.argument<String>("saleID")!!,
          result
        )
      }
      "diagnosisRequest" -> {
        diagnosisRequest(
          call.argument<String>("POIID")!!,
          call.argument<String>("saleID")!!,
          call.argument<Boolean>("hostDiagnosisFlag") ?: false,
          result
        )
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  fun dispose(result: Result) {
    Log.d(tag, "---> dispose()")
    client = null
    terminalLocalAPI = null
    securityKey = null
    sslContext = null
    currentServiceID = null
    result.success(true)
  }

  fun init(ipAddress: String, keyVersion: Int, keyIdentifier: String, keyPassphrase: String, testEnvironment: Boolean = false, result: Result) {
    Log.d(tag, "---> init()")

    if (client != null) {
        Log.d(tag, "Already initialized, disposing first...")
        client = null
        terminalLocalAPI = null
        securityKey = null
        sslContext = null
    }

    try {
      val environment = if (testEnvironment) Environment.TEST else Environment.LIVE
      val config = Config()

      config.setTerminalApiLocalEndpoint("https://" + ipAddress)
      config.setEnvironment(environment)
      config.setHostnameVerifier(TerminalLocalAPIHostnameVerifier(environment))

      sslContext = getSSLContext(context)
      config.setSSLContext(sslContext)

      client = Client(config)
      client!!.setEnvironment(environment, null)

      // Set timeout as recommended by Adyen (120s+)
      config.setReadTimeoutMillis(130000)
      config.setConnectionTimeoutMillis(130000)
      
      securityKey = SecurityKey()
      securityKey!!.setKeyVersion(keyVersion)
      securityKey!!.setAdyenCryptoVersion(1)
      securityKey!!.setKeyIdentifier(keyIdentifier)
      securityKey!!.setPassphrase(keyPassphrase)

      terminalLocalAPI = TerminalLocalAPI(client, securityKey)
      Log.d(tag, "---> exit init()")

      result.success(true)

    } catch (e: Exception) {
      result.error("INIT_ERROR", "Init Error", null)
    }
  }

  fun getSSLContext(context: Context): SSLContext {
    Log.d(tag, "---> getSSLContext()")
    // load root certificate from assets
    certificateInputStream = context.assets.open("adyen-terminalfleet-test.pem")

    // load certificate into keyStore object from input stream
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)  // Initialize the KeyStore
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val adyenRootCertificate: X509Certificate = certificateFactory.generateCertificate(certificateInputStream!!) as X509Certificate
    keyStore.setCertificateEntry("adyenRootCertificate", adyenRootCertificate)
    certificateInputStream?.close()
    // init TrustManagerFactory using the KeyStore
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)

    // init SSLContext using the TrustManager
    val sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(null, trustManagerFactory.trustManagers, SecureRandom())

    
    Log.d(tag, "---> exit getSSLContext()")

    return sslContext
  }

  private val requestExecutor = Executors.newSingleThreadExecutor()
  private val abortAndStatusExecutor = Executors.newSingleThreadExecutor()

  private fun paymentRequest(amount: Double, POIID: String, saleID: String, result: Result) {
    Log.d(tag, "---> paymentRequest()")
    val request: TerminalAPIRequest? = createPaymentRequest(amount, POIID, saleID)
    
    val transactionID = request?.saleToPOIRequest?.paymentRequest?.saleData?.saleTransactionID?.transactionID

    logAndStoreJson(context,"PaymentRequest", request)
    requestExecutor.submit {
      try {
        if (terminalLocalAPI == null) {
          Handler(Looper.getMainLooper()).post {
             result.error("NOT_INITIALIZED", "Adyen API not initialized", null)
          }
          return@submit
        }
        val response: TerminalAPIResponse = terminalLocalAPI!!.request(request)
        logAndStoreJson(context, "PaymentResponse", response)
        val saleToPOIResponse = response.getSaleToPOIResponse()
        val messageHeader = saleToPOIResponse.getMessageHeader()
        val paymentResponse = saleToPOIResponse.getPaymentResponse()
        
        val responseMap = mutableMapOf<String, Any?>(
          "serviceID" to messageHeader.getServiceID(),
          "POIID" to messageHeader.getPOIID(),
          "saleID" to messageHeader.getSaleID()
        )

        if (paymentResponse != null) {
          responseMap["result"] = paymentResponse.getResponse().getResult().value()
          responseMap["errorCondition"] = paymentResponse.getResponse().getErrorCondition()?.value()
          
          val additionalResponse = paymentResponse.getResponse().getAdditionalResponse()
          if (additionalResponse != null) {
            responseMap["additionalResponse"] = String(Base64.decodeBase64(additionalResponse))
          }

          val paymentReceiptList = parsePaymentReceipts(paymentResponse.getPaymentReceipt() ?: emptyList())
          responseMap["paymentReceipt"] = paymentReceiptList

          val poiData = paymentResponse.getPOIData()
          if (poiData != null) {
            val transactionIdentification = poiData.getPOITransactionID()
            if (transactionIdentification != null) {
              responseMap["transaction"] = mapOf(
                "transactionID" to transactionIdentification.getTransactionID(),
                "timeStamp" to transactionIdentification.getTimeStamp()?.toXMLFormat(),
              )
            }
          }
        }

        logResponseSummary("PaymentResponse", responseMap["result"] as? String, responseMap["errorCondition"] as? String, messageHeader.getServiceID())

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
        }
      } catch (e: java.net.SocketTimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: java.io.IOException) {
        Handler(Looper.getMainLooper()).post {
          result.error("NETWORK_ERROR", "Network communication failed", null)
        }
      } catch (e: TimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: Exception) {
        Handler(Looper.getMainLooper()).post {
          result.error("ERROR", e.message, null)
        }
      }
    }
    Log.d(tag, "---> exit paymentRequest()")
  }

  private fun parsePaymentReceipts(paymentReceipts: List<PaymentReceipt>) : List<Map<String, Any?>> {
    return paymentReceipts.map { receipt ->
      val outputContent = receipt.getOutputContent()
      val outputTexts = outputContent.getOutputText()

      val outputTextList = outputTexts.map { outputText ->
        mutableMapOf<String, Any?>(
          "text" to outputText.getText(),
          "endOfLineFlag" to outputText.isEndOfLineFlag()
        ).apply {
          val characterStyle = outputText.getCharacterStyle()
          if (characterStyle != null) {
            this["characterStyle"] = characterStyle.value()
          }
        }
      }

      mapOf(
        "requiredSignatureFlag" to receipt.isRequiredSignatureFlag(),
        "documentQualifier" to receipt.getDocumentQualifier().value(),
        "outputContent" to mapOf(
          "outputFormat" to outputContent.getOutputFormat().value(),
          "outputText" to outputTextList
        )
      )
    }
  }

  private fun refundRequest(transactionID: String, POIID: String, saleID: String, refundAmount: Double?, currencyCode: String, result: Result) {
    Log.d(tag, "---> refundRequest()")
    val request: TerminalAPIRequest? = createRefundRequest(transactionID, POIID, saleID, refundAmount, currencyCode)
    logAndStoreJson(context,"RefundRequest", request)
    requestExecutor.submit {
      try {
        if (terminalLocalAPI == null) {
          Handler(Looper.getMainLooper()).post {
             result.error("NOT_INITIALIZED", "Adyen API not initialized", null)
          }
          return@submit
        }
        val response: TerminalAPIResponse = terminalLocalAPI!!.request(request)
        logAndStoreJson(context, "RefundResponse", response)
        val saleToPOIResponse = response.getSaleToPOIResponse()
        val reversalResponse = saleToPOIResponse.getReversalResponse()
        
        val responseMap = mutableMapOf<String, Any?>()
        
        if (reversalResponse != null) {
          responseMap["result"] = reversalResponse.getResponse().getResult().value()
          responseMap["errorCondition"] = reversalResponse.getResponse().getErrorCondition()?.value()
          responseMap["reversedAmount"] = reversalResponse.getReversedAmount()?.toPlainString()
          
          val additionalResponse = reversalResponse.getResponse().getAdditionalResponse()
          if (additionalResponse != null) {
            responseMap["additionalResponse"] = String(Base64.decodeBase64(additionalResponse))
          }

          val poiData = reversalResponse.getPOIData()
          if (poiData != null) {
            val transactionIdentification = poiData.getPOITransactionID()
            if (transactionIdentification != null) {
              responseMap["transaction"] = mapOf(
                "transactionID" to transactionIdentification.getTransactionID(),
                "timeStamp" to transactionIdentification.getTimeStamp()?.toXMLFormat(),
              )
            }
          }
        } else {
            responseMap["result"] = "Failure"
            responseMap["errorCondition"] = "EmptyResponse"
        }

        logResponseSummary("RefundResponse", responseMap["result"] as? String, responseMap["errorCondition"] as? String)

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
        }
      } catch (e: java.net.SocketTimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: java.io.IOException) {
        Handler(Looper.getMainLooper()).post {
          result.error("NETWORK_ERROR", "Network communication failed", null)
        }
      } catch (e: TimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: Exception) {
        Handler(Looper.getMainLooper()).post {
          result.error("ERROR", e.message, null)
        }
      }
    }
    Log.d(tag, "---> exit refundRequest()")
  }

    private fun statusRequest(transactionServiceID: String, statusRequestType: MessageCategoryType, POIID: String, saleID: String, result: Result) {
    Log.d(tag, "---> statusRequest()")
    val request: TerminalAPIRequest? = createStatusRequest(transactionServiceID, statusRequestType, POIID, saleID)
    logAndStoreJson(context,"StatusRequest", request)
    abortAndStatusExecutor.submit {
      try {
        if (terminalLocalAPI == null) {
          Handler(Looper.getMainLooper()).post {
             result.error("NOT_INITIALIZED", "Adyen API not initialized", null)
          }
          return@submit
        }
        val response: TerminalAPIResponse = terminalLocalAPI!!.request(request)
        logAndStoreJson(context, "StatusResponse", response)
        val saleToPOIResponse = response.getSaleToPOIResponse()
        val transactionStatusResponse = saleToPOIResponse.getTransactionStatusResponse()
        val messageReference = transactionStatusResponse.getMessageReference()
        val repeatedMessageResponse = transactionStatusResponse.getRepeatedMessageResponse()

        val paymentResponseMap = repeatedMessageResponse?.getRepeatedResponseMessageBody()?.getPaymentResponse()?.let {
          val amountsResp = it.getPaymentResult()?.getAmountsResp() // Safely get AmountsResp
          mapOf(
            "result" to it.getResponse().getResult().value(),
            "transactionID" to it.getPOIData().getPOITransactionID().getTransactionID(),
            "timeStamp" to it.getPOIData().getPOITransactionID().getTimeStamp().toXMLFormat(),
            "authorisedAmount" to amountsResp?.getAuthorizedAmount()?.toPlainString()
          ).filterValues { it != null }
        }

        val reversalResponseMap = repeatedMessageResponse?.getRepeatedResponseMessageBody()?.getReversalResponse()?.let {
          mapOf(
            "result" to it.getResponse().getResult().value(),
            "transactionID" to it.getPOIData().getPOITransactionID().getTransactionID(),
            "timeStamp" to it.getPOIData().getPOITransactionID().getTimeStamp().toXMLFormat(),
            "reversedAmount" to it.getReversedAmount().toPlainString()
          )
        }

        val additionalResponse: String? = transactionStatusResponse.getResponse().getAdditionalResponse()
        var decodedAdditionalResponse = "";

        if (additionalResponse != null) {
          decodedAdditionalResponse =
            String(Base64.decodeBase64(additionalResponse))
        }

        val responseMap = mapOf(
          // "result" - success if transaction processed, failure if not processed (inProgress or notFound)
          "result" to transactionStatusResponse.getResponse().getResult().value(),
          "transactionResult" to mapOf(
            "paymentResponse" to paymentResponseMap,
            "reversalResponse" to reversalResponseMap
          ).filterValues { it != null }, // exclude null values from the transactionResult map
          "transactionReference" to mapOf(
            "serviceID" to messageReference?.getServiceID(),
            "POIID" to messageReference?.getPOIID(),
            "saleID" to messageReference?.getSaleID(),
            "messageCategory" to messageReference?.getMessageCategory()?.value()
          ),
          "errorCondition" to transactionStatusResponse.getResponse().getErrorCondition()?.value(),
          "additionalResponse" to decodedAdditionalResponse,
        )

        logResponseSummary("StatusResponse", transactionStatusResponse.getResponse().getResult().value(), transactionStatusResponse.getResponse().getErrorCondition()?.value(), messageReference?.getServiceID())

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
        }
      } catch (e: java.net.SocketTimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: java.io.IOException) {
        Handler(Looper.getMainLooper()).post {
          result.error("NETWORK_ERROR", "Network communication failed", null)
        }
      } catch (e: TimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: Exception) {
        Handler(Looper.getMainLooper()).post {
          result.error("ERROR", e.message, null)
        }
      }
    }
    Log.d(tag, "---> exit statusRequest()")
  }

  private fun abortRequest(POIID: String, saleID: String, result: Result) {
    Log.d(tag, "---> abortRequest()")
    if (currentServiceID == null) {
      result.error("INVALID_STATE", "No ongoing payment request. Cannot proceed with abort request.", null)
      return
    }

    val request: TerminalAPIRequest? = createAbortRequest(currentServiceID!!, POIID, saleID)
    logAndStoreJson(context,"AbortRequest", request)
    abortAndStatusExecutor.submit {
      try {
        if (terminalLocalAPI == null) {
          Handler(Looper.getMainLooper()).post {
             result.error("NOT_INITIALIZED", "Adyen API not initialized", null)
          }
          return@submit
        }
        terminalLocalAPI!!.request(request)
        Handler(Looper.getMainLooper()).post {
          result.success(null)
        }
      } catch (e: java.net.SocketTimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: java.io.IOException) {
        Handler(Looper.getMainLooper()).post {
          result.error("NETWORK_ERROR", "Network communication failed", null)
        }
      } catch (e: TimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: Exception) {
        Handler(Looper.getMainLooper()).post {
          result.error("ERROR", e.message, null)
        }
      }
    }
    Log.d(tag, "---> exit abortRequest()")
  }

  private fun diagnosisRequest(POIID: String, saleID: String, hostDiagnosisFlag: Boolean, result: Result) {
    Log.d(tag, "---> diagnosisRequest()")
    val request: TerminalAPIRequest? = createDiagnosisRequest(POIID, saleID, hostDiagnosisFlag)
    logAndStoreJson(context,"DiagnosisRequest", request)
    abortAndStatusExecutor.submit {
      try {
        if (terminalLocalAPI == null) {
          Handler(Looper.getMainLooper()).post {
             result.error("NOT_INITIALIZED", "Adyen API not initialized", null)
          }
          return@submit
        }
        val response: TerminalAPIResponse = terminalLocalAPI!!.request(request)
        logAndStoreJson(context, "DiagnosisResponse", response)
        
        val saleToPOIResponse = response.getSaleToPOIResponse()
        val diagnosisResponse = saleToPOIResponse.getDiagnosisResponse()
        val messageHeader = saleToPOIResponse.getMessageHeader()

        val responseMap = mutableMapOf<String, Any?>(
          "result" to diagnosisResponse.getResponse().getResult().value(),
          "serviceID" to messageHeader.getServiceID(),
          "POIID" to messageHeader.getPOIID(),
          "saleID" to messageHeader.getSaleID(),
          "errorCondition" to diagnosisResponse.getResponse().getErrorCondition()?.value(),
          "additionalResponse" to diagnosisResponse.getResponse().getAdditionalResponse(),
        )

        val poiStatus = diagnosisResponse.getPOIStatus()
        if (poiStatus != null) {
          responseMap["poiStatus"] = mapOf(
            "GlobalStatus" to poiStatus.getGlobalStatus().value(),
            "CommunicationOKFlag" to poiStatus.isCommunicationOKFlag(),
            "PrinterStatus" to poiStatus.getPrinterStatus()?.value()
          )
        }

        val hostStatus = diagnosisResponse.getHostStatus()
        if (hostStatus != null && hostStatus.isNotEmpty()) {
          responseMap["hostStatus"] = hostStatus.map {
            mapOf("IsReachableFlag" to it.isIsReachableFlag())
          }
        }

        logResponseSummary("DiagnosisResponse", diagnosisResponse.getResponse().getResult().value(), diagnosisResponse.getResponse().getErrorCondition()?.value(), messageHeader.getServiceID())

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
        }
      } catch (e: java.net.SocketTimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: java.io.IOException) {
        Handler(Looper.getMainLooper()).post {
          result.error("NETWORK_ERROR", "Network communication failed", null)
        }
      } catch (e: TimeoutException) {
        Handler(Looper.getMainLooper()).post {
          result.error("TIMED_OUT", "Request timed out", null)
        }
      } catch (e: Exception) {
        Handler(Looper.getMainLooper()).post {
          result.error("ERROR", e.message, null)
        }
      }
    }
    Log.d(tag, "---> exit diagnosisRequest()")
  }

  private fun createPaymentRequest(amount: Double, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID()
    val transactionID = java.util.UUID.randomUUID().toString().take(10)

    val saleToPOIRequest = SaleToPOIRequest()
    val messageHeader = MessageHeader()
    messageHeader.setProtocolVersion("3.0")
    messageHeader.setMessageClass(MessageClassType.SERVICE)
    messageHeader.setMessageCategory(MessageCategoryType.PAYMENT)
    messageHeader.setMessageType(MessageType.REQUEST)
    messageHeader.setSaleID(saleID)
    messageHeader.setServiceID(serviceID)
    messageHeader.setPOIID(POIID)
    saleToPOIRequest.setMessageHeader(messageHeader)

    val paymentRequest = PaymentRequest()
    val saleData = SaleData()
    val saleTransactionID = TransactionIdentification()
    saleTransactionID.setTransactionID(transactionID)
    val timeStamp = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar())
    saleTransactionID.setTimeStamp(
      timeStamp
    )
    saleData.setSaleTransactionID(saleTransactionID)
    paymentRequest.setSaleData(saleData)

    val paymentTransaction = PaymentTransaction()
    val amountsReq = AmountsReq()
    amountsReq.setCurrency("AUD")
    amountsReq.setRequestedAmount(BigDecimal.valueOf(amount))
    paymentTransaction.setAmountsReq(amountsReq)
    paymentRequest.setPaymentTransaction(paymentTransaction)
    saleToPOIRequest.setPaymentRequest(paymentRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    currentServiceID = serviceID

    return terminalAPIRequest
  }

  private fun createRefundRequest(transactionID: String, POIID: String, saleID: String, refundAmount: Double?, currencyCode: String): TerminalAPIRequest? {

    val serviceID = createServiceID()

    val saleToPOIRequest = SaleToPOIRequest()
    val messageHeader = MessageHeader()
    messageHeader.setProtocolVersion("3.0")
    messageHeader.setMessageClass(MessageClassType.SERVICE)
    messageHeader.setMessageCategory(MessageCategoryType.REVERSAL)
    messageHeader.setMessageType(MessageType.REQUEST)
    messageHeader.setSaleID(saleID)
    messageHeader.setServiceID(serviceID)
    messageHeader.setPOIID(POIID)
    saleToPOIRequest.setMessageHeader(messageHeader)

    val reversalRequest = ReversalRequest()
    val originalPOITransaction = OriginalPOITransaction()
    val pOITransactionID = TransactionIdentification()

    pOITransactionID.setTransactionID(transactionID)
    pOITransactionID.setTimeStamp(
      DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar())
    )

    originalPOITransaction.setPOITransactionID(pOITransactionID)
    reversalRequest.setOriginalPOITransaction(originalPOITransaction)
    reversalRequest.setReversalReason(ReversalReasonType.MERCHANT_CANCEL)

    if (refundAmount != null) {
      reversalRequest.setReversedAmount(BigDecimal.valueOf(refundAmount))

      val saleData = SaleData()
      val saleToAcquirerData = SaleToAcquirerData()
      saleToAcquirerData.setCurrency(currencyCode)
      saleData.setSaleToAcquirerData(saleToAcquirerData)
      val saleTransactionID = TransactionIdentification()
      saleTransactionID.setTimeStamp(
        DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar())
      )
      saleTransactionID.setTransactionID(transactionID + "_refund")
      saleData.setSaleTransactionID(saleTransactionID)
      reversalRequest.setSaleData(saleData)
    }

    saleToPOIRequest.setReversalRequest(reversalRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    currentServiceID = serviceID

    return terminalAPIRequest
  }

  private fun createStatusRequest(transactionServiceID: String, statusRequestType: MessageCategoryType, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID()

    val saleToPOIRequest = SaleToPOIRequest()
    val messageHeader = MessageHeader()
    messageHeader.setProtocolVersion("3.0")
    messageHeader.setMessageClass(MessageClassType.SERVICE)
    messageHeader.setMessageCategory(MessageCategoryType.TRANSACTION_STATUS)
    messageHeader.setMessageType(MessageType.REQUEST)
    messageHeader.setSaleID(saleID)
    messageHeader.setServiceID(serviceID)
    messageHeader.setPOIID(POIID)
    saleToPOIRequest.setMessageHeader(messageHeader)

    val transactionStatusRequest = TransactionStatusRequest()
    transactionStatusRequest.setReceiptReprintFlag(true)
    transactionStatusRequest.getDocumentQualifier().add(DocumentQualifierType.CASHIER_RECEIPT)
    transactionStatusRequest.getDocumentQualifier().add(DocumentQualifierType.CUSTOMER_RECEIPT)
    val messageReference = MessageReference()
    messageReference.setMessageCategory(statusRequestType)
    messageReference.setSaleID(saleID)
    messageReference.setServiceID(transactionServiceID)
    transactionStatusRequest.setMessageReference(messageReference)

    saleToPOIRequest.setTransactionStatusRequest(transactionStatusRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    return terminalAPIRequest
  }

  private fun createAbortRequest(paymentRequestServiceID: String, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID()

    val saleToPOIRequest = SaleToPOIRequest()
    val messageHeader = MessageHeader()
    messageHeader.setProtocolVersion("3.0")
    messageHeader.setMessageClass(MessageClassType.SERVICE)
    messageHeader.setMessageCategory(MessageCategoryType.ABORT)
    messageHeader.setMessageType(MessageType.REQUEST)
    messageHeader.setSaleID(saleID)
    messageHeader.setServiceID(serviceID)
    messageHeader.setPOIID(POIID)
    saleToPOIRequest.setMessageHeader(messageHeader)

    val abortRequest = AbortRequest()
    abortRequest.setAbortReason("MerchantAbort")
    val messageReference = MessageReference()
    messageReference.setMessageCategory(MessageCategoryType.PAYMENT)
    messageReference.setSaleID(saleID)
    messageReference.setPOIID(POIID)

    messageReference.setServiceID(paymentRequestServiceID)
    abortRequest.setMessageReference(messageReference)

    saleToPOIRequest.setAbortRequest(abortRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    return terminalAPIRequest
  }

  private fun createDiagnosisRequest(POIID: String, saleID: String, hostDiagnosisFlag: Boolean): TerminalAPIRequest? {
    val serviceID = createServiceID()

    val saleToPOIRequest = SaleToPOIRequest()
    val messageHeader = MessageHeader()
    messageHeader.setProtocolVersion("3.0")
    messageHeader.setMessageClass(MessageClassType.SERVICE)
    messageHeader.setMessageCategory(MessageCategoryType.DIAGNOSIS)
    messageHeader.setMessageType(MessageType.REQUEST)
    messageHeader.setSaleID(saleID)
    messageHeader.setServiceID(serviceID)
    messageHeader.setPOIID(POIID)
    saleToPOIRequest.setMessageHeader(messageHeader)

    val diagnosisRequest = DiagnosisRequest()
    diagnosisRequest.setHostDiagnosisFlag(hostDiagnosisFlag)
    
    saleToPOIRequest.setDiagnosisRequest(diagnosisRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    return terminalAPIRequest
  }

  /* // Simplified - replaced with logResponseSummary()
fun printSaleToPOIResponseInfo(response: SaleToPOIResponse?) {
  if (response == null) {
    println("SaleToPOIResponse is null.")
    return
  }
  println("SaleToPOIResponse Information:")
  if (response.getMessageHeader() != null) {
    System.out.println("Message Header: " + response.getMessageHeader())
    printMessageHeaderInfo(response.getMessageHeader())
  }
  if (response.getBalanceInquiryResponse() != null) {
    System.out.println("Balance Inquiry Response: " + response.getBalanceInquiryResponse())
  }
  if (response.getBatchResponse() != null) {
    System.out.println("Batch Response: " + response.getBatchResponse())
  }
  if (response.getCardAcquisitionResponse() != null) {
    System.out.println("Card Acquisition Response: " + response.getCardAcquisitionResponse())
  }
  if (response.getAdminResponse() != null) {
    System.out.println("Admin Response: " + response.getAdminResponse())
  }
  if (response.getDiagnosisResponse() != null) {
    System.out.println("Diagnosis Response: " + response.getDiagnosisResponse())
  }
  if (response.getDisplayResponse() != null) {
    System.out.println("Display Response: " + response.getDisplayResponse())
  }
  if (response.getEnableServiceResponse() != null) {
    System.out.println("Enable Service Response: " + response.getEnableServiceResponse())
  }
  if (response.getGetTotalsResponse() != null) {
    System.out.println("Get Totals Response: " + response.getGetTotalsResponse())
  }
  if (response.getInputResponse() != null) {
    System.out.println("Input Response: " + response.getInputResponse())
  }
  if (response.getLoginResponse() != null) {
    System.out.println("Login Response: " + response.getLoginResponse())
  }
  if (response.getLogoutResponse() != null) {
    System.out.println("Logout Response: " + response.getLogoutResponse())
  }
  if (response.getLoyaltyResponse() != null) {
    System.out.println("Loyalty Response: " + response.getLoyaltyResponse())
  }
  if (response.getPaymentResponse() != null) {
    System.out.println("Payment Response: " + response.getPaymentResponse())
    printPaymentResponseInfo(response.getPaymentResponse())
  }
  if (response.getPINResponse() != null) {
    System.out.println("PIN Response: " + response.getPINResponse())
  }
  if (response.getPrintResponse() != null) {
    System.out.println("Print Response: " + response.getPrintResponse())
  }
  if (response.getCardReaderInitResponse() != null) {
    System.out.println("Card Reader Init Response: " + response.getCardReaderInitResponse())
  }
  if (response.getCardReaderAPDUResponse() != null) {
    System.out.println("Card Reader APDU Response: " + response.getCardReaderAPDUResponse())
  }
  if (response.getCardReaderPowerOffResponse() != null) {
    System.out.println("Card Reader Power Off Response: " + response.getCardReaderPowerOffResponse())
  }
  if (response.getReconciliationResponse() != null) {
    System.out.println("Reconciliation Response: " + response.getReconciliationResponse())
  }
  if (response.getReversalResponse() != null) {
    System.out.println("Reversal Response: " + response.getReversalResponse())
    printReversalResponseDetails(response.getReversalResponse())
  }
  if (response.getSoundResponse() != null) {
    System.out.println("Sound Response: " + response.getSoundResponse())
  }
  if (response.getStoredValueResponse() != null) {
    System.out.println("Stored Value Response: " + response.getStoredValueResponse())
  }
  if (response.getTransactionStatusResponse() != null) {
    System.out.println("Transaction Status Response: " + response.getTransactionStatusResponse())
    printResponseDetails(response.getTransactionStatusResponse().getResponse())
  }
  if (response.getTransmitResponse() != null) {
    System.out.println("Transmit Response: " + response.getTransmitResponse())
  }
  if (response.getSecurityTrailer() != null) {
    System.out.println("Security Trailer: " + response.getSecurityTrailer())
  }
} */

  
  
  

  
  
  
  

  fun createServiceID(): String {
    return System.currentTimeMillis().toString().takeLast(10)
  }

  fun logAndStoreJson(context: Context, type: String, data: Any?) {
    val gson = GsonBuilder().setPrettyPrinting().create()
    val jsonData = gson.toJson(data)

    // Log concise summary to Logcat
    Log.d(tag, "$type: ${jsonData.take(200)}${if(jsonData.length > 200) "..." else ""}")

    // Save to file with timestamp
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
    val filename = "${type}_${timestamp}.json"
    val file = File(context.filesDir, filename)
    file.writeText(jsonData)
    Log.d(tag, "Saved $type to: ${file.name}")
  }

  private fun logResponseSummary(type: String, result: String?, errorCondition: String?, serviceID: String? = null) {
    val summary = mutableListOf<String>().apply {
      add(type)
      result?.let { add("result=$it") }
      errorCondition?.let { add("error=$it") }
      serviceID?.let { add("serviceID=$it") }
    }.joinToString(", ")
    Log.d(tag, summary)
  }

}
