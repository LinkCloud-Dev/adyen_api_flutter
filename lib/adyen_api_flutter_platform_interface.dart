import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'adyen_api_flutter_method_channel.dart';

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

  Future<void> init() {
    return _instance.init();
  }

  Future<void> request() {
    return _instance.request();
  }
}
