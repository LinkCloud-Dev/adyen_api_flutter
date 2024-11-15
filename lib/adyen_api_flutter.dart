
import 'adyen_api_flutter_platform_interface.dart';

class AdyenApiFlutter {
  Future<String?> getPlatformVersion() {
    return AdyenApiFlutterPlatform.instance.getPlatformVersion();
  }

  Future<void> init() {
    return AdyenApiFlutterPlatform.instance.init();
  }

  Future<void> request() {
    return AdyenApiFlutterPlatform.instance.request();
  }
}
