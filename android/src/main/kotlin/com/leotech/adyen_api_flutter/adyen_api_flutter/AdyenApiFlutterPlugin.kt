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
import com.adyen.model.nexo.*
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

/** AdyenApiFlutterPlugin */
class AdyenApiFlutterPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel

  // test logging
  val tag = "LOG"

  private lateinit var client: Client
  private lateinit var service: Service
  private lateinit var securityKey: SecurityKey
  private lateinit var terminalLocalAPI: TerminalLocalAPI
  private lateinit var terminalLocalAPIUnencrypted: TerminalLocalAPIUnencrypted
  private lateinit var certificateInputStream: InputStream
  private lateinit var sslContext: SSLContext
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
          result
        )
      }
      "statusRequest" -> {
        val statusRequestTypeString = call.argument<String>("statusRequestType")!!
        println(">>>> string of type: " + statusRequestTypeString)
        val statusRequestType = statusRequestTypeString?.let {
          try {
            MessageCategoryType.valueOf(it)
          } catch (e: IllegalArgumentException) {
            null // Handle invalid enum value gracefully
          }
        }
        statusRequest(
          call.argument<String>("transactionServiceID")!!,
          statusRequestType!!,
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
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  fun init(ipAddress: String, keyVersion: Int, keyIdentifier: String, keyPassphrase: String, testEnvironment: Boolean = false, result: Result) {
    var initialized = true
    Log.d(tag, "---> init()")

    try {
      client
    } catch (e: UninitializedPropertyAccessException) {
      initialized = false
    }

    if (initialized) {
//      result.error("INITIALIZED", "Initialized Already.", null)
      result.success(null)
      return
    }

    try {
      val environment = if (testEnvironment) Environment.TEST else Environment.LIVE
      val config = Config()

      // URL of the terminal,
      // for example https://192.168.68.117, WITHOUT the port/nexo part :8443/nexo/
      config.setTerminalApiLocalEndpoint("https://" + ipAddress)
      config.setEnvironment(environment)
      config.setHostnameVerifier(TerminalLocalAPIHostnameVerifier(environment))
      // init SSLContext
      sslContext = getSSLContext(context)
      config.setSSLContext(sslContext)

      // set Client
      client = Client(config)
      client.setEnvironment(environment, null)

      // config SecurityKey
      securityKey = SecurityKey()
      securityKey.setKeyVersion(keyVersion)
      securityKey.setAdyenCryptoVersion(1)
      securityKey.setKeyIdentifier(keyIdentifier)
      securityKey.setPassphrase(keyPassphrase)

      terminalLocalAPI = TerminalLocalAPI(client, securityKey)
      Log.d(tag, "---> exit init()")

      result.success(null)

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
    val adyenRootCertificate: X509Certificate = certificateFactory.generateCertificate(certificateInputStream) as X509Certificate
    keyStore.setCertificateEntry("adyenRootCertificate", adyenRootCertificate)
    certificateInputStream.close()
    // init TrustManagerFactory using the KeyStore
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)

    // init SSLContext using the TrustManager
    val sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(null, trustManagerFactory.trustManagers, SecureRandom())

    // log certificate and SSLContext
    logCertificateFromKeyStore(keyStore)
    logSSLContextInfo(sslContext, trustManagerFactory)

    Log.d(tag, "---> exit getSSLContext()")

    return sslContext
  }

  private val requestExecutor = Executors.newSingleThreadExecutor()
  private val abortAndStatusExecutor = Executors.newSingleThreadExecutor()

  private fun paymentRequest(amount: Double, POIID: String, saleID: String, result: Result) {
    Log.d(tag, "---> paymentRequest()")
    val request: TerminalAPIRequest? = createPaymentRequest(amount, POIID, saleID)
    requestExecutor.submit {
      try {
        val response: TerminalAPIResponse = terminalLocalAPI.request(request)
        val saleToPOIResponse = response.getSaleToPOIResponse()
        val messageHeader = saleToPOIResponse.getMessageHeader()
        val paymentResponse = saleToPOIResponse.getPaymentResponse()
        val POIData = paymentResponse.getPOIData()
        val transactionIdentification = POIData.getPOITransactionID()

        val responseMap = mapOf(
          "result" to paymentResponse.getResponse().getResult().value(),
          "serviceID" to messageHeader.getServiceID(),
          "POIID" to messageHeader.getPOIID(),
          "saleID" to messageHeader.getSaleID(),
          "transaction" to mapOf(
            "transactionID" to transactionIdentification.getTransactionID(),
            "timeStamp" to transactionIdentification.getTimeStamp().toXMLFormat(),
          ),
          "errorCondition" to paymentResponse.getResponse().getErrorCondition()?.value(),
          "additionalResponse" to paymentResponse.getResponse().getAdditionalResponse(),
        )
        printSaleToPOIResponseInfo(response.getSaleToPOIResponse())

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
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

   // referenced refunds (* connected to original payment)
  private fun refundRequest(transactionID: String, POIID: String, saleID: String, result: Result) {
    Log.d(tag, "---> refundRequest()")
    val request: TerminalAPIRequest? = createRefundRequest(transactionID, POIID, saleID)
    requestExecutor.submit {
      try {
        val response: TerminalAPIResponse = terminalLocalAPI.request(request)
        val saleToPOIResponse = response.getSaleToPOIResponse()
        val reversalResponse = saleToPOIResponse.getReversalResponse()
        val POIData = reversalResponse.getPOIData()
        val transactionIdentification = POIData.getPOITransactionID()

        val responseMap = mapOf(
          "result" to reversalResponse.getResponse().getResult().value(),
          "transaction" to mapOf(
            "transactionID" to transactionIdentification.getTransactionID(),
            "timeStamp" to transactionIdentification.getTimeStamp().toXMLFormat(),
          ),
          "amount" to reversalResponse.getReversedAmount()?.toPlainString(),
          "errorCondition" to reversalResponse.getResponse().getErrorCondition()?.value(),
          "additionalResponse" to reversalResponse.getResponse().getAdditionalResponse(),

        )
        printSaleToPOIResponseInfo(saleToPOIResponse)

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
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
    abortAndStatusExecutor.submit {
      try {
        val response: TerminalAPIResponse = terminalLocalAPI.request(request)
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
          "additionalResponse" to transactionStatusResponse.getResponse().getAdditionalResponse()
        )

        printSaleToPOIResponseInfo(saleToPOIResponse)

        Handler(Looper.getMainLooper()).post {
          result.success(responseMap)
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
    // check if there is an ongoing paymentRequest to abort
    if (currentServiceID == null) {
      result.error("INVALID_STATE", "No ongoing payment request. Cannot proceed with abort request.", null)
      return
    }

    val request: TerminalAPIRequest? = createAbortRequest(currentServiceID!!, POIID, saleID)
    abortAndStatusExecutor.submit {
      try {
        // abort request response is null
        // response returned to payment request object
        terminalLocalAPI.request(request)
        Handler(Looper.getMainLooper()).post {
          result.success(null)
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

  private fun createPaymentRequest(amount: Double, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID() //"YOUR_UNIQUE_ATTEMPT_ID"

    // Your reference to identify a payment.
    // We recommend using a unique value per payment.
    // In your Customer Area and Adyen reports, this will show as the merchant reference for the transaction.
    val transactionID = java.util.UUID.randomUUID().toString().take(10) //"YOUR_UNIQUE_TRANSACTION_ID"

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

  private fun createRefundRequest(transactionID: String, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID() //"YOUR_UNIQUE_ATTEMPT_ID"

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
    // transactionID and timeStamp of original transaction
    val originalPOITransaction = OriginalPOITransaction()
    val pOITransactionID = TransactionIdentification()


    println(">> creating Refund Request: " + transactionID)
    pOITransactionID.setTransactionID(transactionID)
    pOITransactionID.setTimeStamp(
      DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar())
    )

    originalPOITransaction.setPOITransactionID(pOITransactionID)
    reversalRequest.setOriginalPOITransaction(originalPOITransaction)
    reversalRequest.setReversalReason(ReversalReasonType.MERCHANT_CANCEL)
    saleToPOIRequest.setReversalRequest(reversalRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    currentServiceID = serviceID

    return terminalAPIRequest
  }

  private fun createStatusRequest(transactionServiceID: String, statusRequestType: MessageCategoryType, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID() //"YOUR_UNIQUE_ATTEMPT_ID"

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

    // serviceID of the transaction you want the status update from
    messageReference.setServiceID(transactionServiceID)
    transactionStatusRequest.setMessageReference(messageReference)

    saleToPOIRequest.setTransactionStatusRequest(transactionStatusRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    return terminalAPIRequest
  }

  private fun createAbortRequest(paymentRequestServiceID: String, POIID: String, saleID: String): TerminalAPIRequest? {

    val serviceID = createServiceID() //"YOUR_UNIQUE_ATTEMPT_ID"

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

    messageReference.setServiceID(paymentRequestServiceID) // Service ID of the payment you're aborting
    abortRequest.setMessageReference(messageReference)

    saleToPOIRequest.setAbortRequest(abortRequest)

    val terminalAPIRequest = TerminalAPIRequest()
    terminalAPIRequest.setSaleToPOIRequest(saleToPOIRequest)

    return terminalAPIRequest
  }

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
}

  fun printPaymentResponseInfo(paymentResponse: PaymentResponse?) {
    if (paymentResponse == null) {
      println("PaymentResponse is null.")
      return
    }
    println("PaymentResponse Information:")

    // Print Response
    if (paymentResponse.getResponse() != null) {
      println("- Response Information:")

      val response = paymentResponse.getResponse()

      // Print Additional Response
      println("Additional Response: ${response.additionalResponse ?: "null"}")

      // Print Result
      println("Result: ${response.result ?: "null"}")

      // Print Error Condition
      println("Error Condition: ${response.errorCondition ?: "null"}")
    } else {
      println("Response: null")
    }

    // Print Sale Data
    if (paymentResponse.getSaleData() != null) {
      val saleData = paymentResponse.getSaleData()
      println("- SaleData Information:")

      // Print Sale Transaction ID
      println("Sale Transaction ID: ${saleData.saleTransactionID ?: "null"}")

      // Print Sale Terminal Data
      println("Sale Terminal Data: ${saleData.saleTerminalData ?: "null"}")

      // Print Sponsored Merchants
      println("Sponsored Merchants:")
      saleData.sponsoredMerchant?.forEach { sponsoredMerchant ->
        println(" - $sponsoredMerchant")
      } ?: println("null")

      // Print Sale to POI Data
      println("Sale to POI Data: ${saleData.saleToPOIData ?: "null"}")

      // Print Sale to Acquirer Data
      println("Sale to Acquirer Data: ${saleData.saleToAcquirerData ?: "null"}")

      // Print Sale to Issuer Data
      println("Sale to Issuer Data: ${saleData.saleToIssuerData ?: "null"}")

      // Print Operator ID
      println("Operator ID: ${saleData.operatorID ?: "null"}")

      // Print Operator Language
      println("Operator Language: ${saleData.operatorLanguage ?: "null"}")

      // Print Shift Number
      println("Shift Number: ${saleData.shiftNumber ?: "null"}")

      // Print Sale Reference ID
      println("Sale Reference ID: ${saleData.saleReferenceID ?: "null"}")

      // Print Token Requested Type
      println("Token Requested Type: ${saleData.tokenRequestedType ?: "null"}")

      // Print Customer Order ID
      println("Customer Order ID: ${saleData.customerOrderID ?: "null"}")

      // Print Customer Order Requests
      println("Customer Order Requests:")
      saleData.customerOrderReq?.forEach { customerOrderReq ->
        println(" - $customerOrderReq")
      } ?: println("null")
    } else {
      println("Sale Data: null")
    }

    // Print POI Data
    if (paymentResponse.getPOIData() != null) {
      System.out.println("POI Data: " + paymentResponse.getPOIData())
      printPOIData(paymentResponse.getPOIData())
    } else {
      println("POI Data: null")
    }

    // Print Payment Result
    if (paymentResponse.getPaymentResult() != null) {
      System.out.println("Payment Result: " + paymentResponse.getPaymentResult())
    } else {
      println("Payment Result: null")
    }

    // Print Loyalty Results
    if (paymentResponse.getLoyaltyResult() != null && !paymentResponse.getLoyaltyResult()
        .isEmpty()
    ) {
      println("Loyalty Results:")
      for (loyaltyResult in paymentResponse.getLoyaltyResult()) {
        println("  - $loyaltyResult")
      }
    } else {
      println("Loyalty Results: none")
    }

    // Print Payment Receipts
    if (paymentResponse.getPaymentReceipt() != null && !paymentResponse.getPaymentReceipt()
        .isEmpty()
    ) {
      println("Payment Receipts:")
      for (paymentReceipt in paymentResponse.getPaymentReceipt()) {
        println("  - $paymentReceipt")
      }
    } else {
      println("Payment Receipts: none")
    }

    // Print Customer Orders
    if (paymentResponse.getCustomerOrder() != null && !paymentResponse.getCustomerOrder()
        .isEmpty()
    ) {
      println("Customer Orders:")
      for (customerOrder in paymentResponse.getCustomerOrder()) {
        println("  - $customerOrder")
      }
    } else {
      println("Customer Orders: none")
    }
  }

  private fun printReversalResponseDetails(reversalResponse: ReversalResponse) {
    // Log or print basic ReversalResponse properties
    Log.d("ReversalResponse", "ReversedAmount: ${reversalResponse.reversedAmount}")
    Log.d("ReversalResponse", "CustomerOrderID: ${reversalResponse.customerOrderID}")

    // Print Response details
    reversalResponse.response?.let { response ->
      Log.d("Response", "Result: ${response.result}")
      Log.d("Response", "ErrorCondition: ${response.errorCondition}")
      Log.d("Response", "AdditionalResponse: ${response.additionalResponse}")
    } ?: Log.d("Response", "Response is null")

    // Print POIData details if available
    reversalResponse.poiData?.let { poiData ->
      Log.d("POIData", "POIData: $poiData") // Customize based on POIData implementation
    } ?: Log.d("POIData", "POIData is null")

    // Print OriginalPOITransaction details if available
    reversalResponse.originalPOITransaction?.let { originalPOITransaction ->
      Log.d("OriginalPOITransaction", "OriginalPOITransaction: $originalPOITransaction") // Customize as needed
    } ?: Log.d("OriginalPOITransaction", "OriginalPOITransaction is null")

    // Print PaymentReceipt list details if available
    reversalResponse.paymentReceipt?.let { receipts ->
      if (receipts.isNotEmpty()) {
        receipts.forEachIndexed { index, receipt ->
          Log.d("PaymentReceipt", "Receipt[$index]: $receipt") // Customize if PaymentReceipt has more fields
        }
      } else {
        Log.d("PaymentReceipt", "PaymentReceipt list is empty")
      }
    } ?: Log.d("PaymentReceipt", "PaymentReceipt is null")
  }

  fun printPOIData(poiData: POIData?) {
    if (poiData == null) {
      println("POIData is null")
      return
    }

    // Retrieve POITransactionID
    val transactionId = poiData.poiTransactionID
    if (transactionId != null) {
      println("POITransactionID:")
      println("\tTransactionID: ${transactionId.transactionID}")
      println("\tTimeStamp: ${transactionId.timeStamp}")
    } else {
      println("POITransactionID is null")
    }

    // Retrieve POIReconciliationID
    val reconciliationId = poiData.poiReconciliationID
    println("POIReconciliationID: ${reconciliationId ?: "Not provided"}")
  }


  fun printMessageHeaderInfo(messageHeader: MessageHeader?) {
    if (messageHeader == null) {
      println("MessageHeader is null.")
      return
    }

    println("MessageHeader Information:")

    // Print Protocol Version
    println("Protocol Version: ${messageHeader.protocolVersion ?: "null"}")

    // Print Message Class
    println("Message Class: ${messageHeader.messageClass ?: "null"}")

    // Print Message Category
    println("Message Category: ${messageHeader.messageCategory ?: "null"}")

    // Print Message Type
    println("Message Type: ${messageHeader.messageType ?: "null"}")

    // Print Service ID
    println("Service ID: ${messageHeader.serviceID ?: "null"}")

    // Print Device ID
    println("Device ID: ${messageHeader.deviceID ?: "null"}")

    // Print Sale ID
    println("Sale ID: ${messageHeader.saleID ?: "null"}")

    // Print POIID
    println("POIID: ${messageHeader.poiid ?: "null"}")
  }

  fun logSSLContextInfo(sslContext: SSLContext, trustManagerFactory: TrustManagerFactory) {
    // Log the protocol used by SSLContext
    Log.d("SSLContextInfo", "SSLContext Protocol: ${sslContext.protocol}")

    // Retrieve TrustManager from the SSLContext
    val trustManagers = trustManagerFactory.trustManagers
    if (trustManagers != null && trustManagers.isNotEmpty()) {
      // Log details of TrustManagers
      for ((index, trustManager) in trustManagers.withIndex()) {
        Log.d("SSLContextInfo", "TrustManager $index: ${trustManager::class.java.name}")

        // If the TrustManager is X509TrustManager, we can log certificate details
        if (trustManager is X509TrustManager) {
          val acceptedIssuers = trustManager.acceptedIssuers
          Log.d("SSLContextInfo", "Number of accepted issuers: ${acceptedIssuers.size}")

          for ((certIndex, cert) in acceptedIssuers.withIndex()) {
            Log.d("SSLContextInfo", "Certificate $certIndex: ${cert.subjectX500Principal}")
            Log.d("SSLContextInfo", "Certificate Issuer: ${cert.issuerX500Principal}")
            Log.d("SSLContextInfo", "Certificate Serial Number: ${cert.serialNumber}")
          }
        }
      }
    } else {
      Log.w("SSLContextInfo", "No TrustManagers found in SSLContext.")
    }
  }

  fun logCertificateFromKeyStore(keyStore: KeyStore) {
    // Log each certificate entry in the KeyStore
    Log.d(tag, "LOG certificate from KeyStore:")
    keyStore.aliases().toList().forEach { alias ->
      val certificate = keyStore.getCertificate(alias)
      if (certificate != null) {
        Log.d(tag, "Alias: $alias")
        Log.d(tag, "Certificate Type: ${certificate.type}")
        Log.d(tag, "Certificate Public Key: ${certificate.publicKey}")
        Log.d(tag, "Certificate: $certificate")
      } else {
        Log.d(tag, "No certificate found for alias: $alias")
      }
    }
  }

  fun printResponseDetails(response: Response?) {
    if (response == null) {
      println("Response is null")
      return
    }

    println("Response Details:")
    println("Additional Response: ${response.additionalResponse ?: "N/A"}")
    println("Result: ${response.result ?: "N/A"}")
    println("Error Condition: ${response.errorCondition ?: "N/A"}")
  }


  fun createServiceID(): String {
    // Your unique ID for this request, consisting of 1-10 alphanumeric characters.
    // Must be unique within the last 48 hours for the terminal (POIID) being used.
    return System.currentTimeMillis().toString().takeLast(10) //"YOUR_UNIQUE_ATTEMPT_ID"
  }

}
