import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'adyen_api_flutter_method_channel.dart';
import 'helper.dart';

abstract class AdyenApiFlutterPlatform extends PlatformInterface {
  /// Constructs a AdyenApiFlutterPlatform.
  AdyenApiFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static AdyenApiFlutterPlatform _instance = MethodChannelAdyenApiFlutter();

  /// The default instance of [AdyenApiFlutterPlatform] to use.
  ///
  /// Defaults to [MethodChannelAdyenApiFlutter].
  static AdyenApiFlutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AdyenApiFlutterPlatform] when
  /// they register themselves.
  static set instance(AdyenApiFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<void> init(String ipAddress, int keyVersion, String keyIdentifier, String keyPassphrase, bool testEnvironment) {
    return _instance.init(ipAddress, keyVersion, keyIdentifier, keyPassphrase, testEnvironment);
  }

  Future<Map<String, dynamic>> paymentRequest(double amount, String POIID, String saleID) {
    return _instance.paymentRequest(amount, POIID, saleID);
  }

  Future<void> abortRequest(String POIID, String saleID) {
    return _instance.abortRequest(POIID, saleID);
  }

  Future<Map<dynamic, dynamic>> refundRequest(String transactionID, String POIID, String saleID) {
    return _instance.refundRequest(transactionID, POIID, saleID);
  }

  Future<Map<dynamic, dynamic>> statusRequest(String transactionServiceID, MessageCategoryType statusRequestType, String POIID, String saleID) {
    return _instance.statusRequest(transactionServiceID, statusRequestType, POIID, saleID);
  }
}
