import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'adyen_api_flutter_platform_interface.dart';

/// An implementation of [AdyenApiFlutterPlatform] that uses method channels.
class MethodChannelAdyenApiFlutter extends AdyenApiFlutterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('adyen_api_flutter');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<void> init() async {
    await methodChannel.invokeMethod('init');
  }

  @override
  Future<void> request() async {
    await methodChannel.invokeMethod('request');
  }
}
