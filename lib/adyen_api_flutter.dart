
import 'adyen_api_flutter_platform_interface.dart';
import 'helper.dart';

class AdyenApiFlutter {
  Future<String?> getPlatformVersion() {
    return AdyenApiFlutterPlatform.instance.getPlatformVersion();
  }

  Future<void> init(String ipAddress, int keyVersion, String keyIdentifier, String keyPassphrase, bool testEnvironment) {
    return AdyenApiFlutterPlatform.instance.init(ipAddress, keyVersion, keyIdentifier, keyPassphrase, testEnvironment);
  }

  Future<Map<dynamic, dynamic>> paymentRequest(double amount, String POIID, {String saleID = "001"}) {
    return AdyenApiFlutterPlatform.instance.paymentRequest(amount, POIID, saleID);
  }

  Future<void> abortRequest(String POIID, {String saleID = "001"}) {
    return AdyenApiFlutterPlatform.instance.abortRequest(POIID, saleID);
  }

  Future<Map<dynamic, dynamic>> refundRequest(String transactionID, String POIID, {String saleID = "001"}) {
    return AdyenApiFlutterPlatform.instance.refundRequest(transactionID, POIID, saleID);
  }

  Future<Map<dynamic, dynamic>> statusRequest(String transactionServiceID, MessageCategoryType statusRequestType, String POIID, {String saleID = "001"}) {
    return AdyenApiFlutterPlatform.instance.statusRequest(transactionServiceID, statusRequestType, POIID, saleID);
  }
}
