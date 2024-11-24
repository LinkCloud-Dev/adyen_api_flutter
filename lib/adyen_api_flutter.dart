
import 'adyen_api_flutter_platform_interface.dart';

class AdyenApiFlutter {
  Future<String?> getPlatformVersion() {
    return AdyenApiFlutterPlatform.instance.getPlatformVersion();
  }

  Future<void> init(String ipAddress, int keyVersion, String keyIdentifier, String keyPassphrase, bool testEnvironment) {
    return AdyenApiFlutterPlatform.instance.init(ipAddress, keyVersion, keyIdentifier, keyPassphrase, testEnvironment);
  }

  Future<void> paymentRequest(double amount, String POIID, {String saleID = "001"}) {
    return AdyenApiFlutterPlatform.instance.paymentRequest(amount, POIID, saleID);
  }

  Future<void> abortRequest(String POIID, {String saleID = "001"}) {
    return AdyenApiFlutterPlatform.instance.abortRequest(POIID, saleID);
  }
}
