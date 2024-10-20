import 'package:flutter_test/flutter_test.dart';
import 'package:adyen_api_flutter/adyen_api_flutter.dart';
import 'package:adyen_api_flutter/adyen_api_flutter_platform_interface.dart';
import 'package:adyen_api_flutter/adyen_api_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockAdyenApiFlutterPlatform
    with MockPlatformInterfaceMixin
    implements AdyenApiFlutterPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final AdyenApiFlutterPlatform initialPlatform = AdyenApiFlutterPlatform.instance;

  test('$MethodChannelAdyenApiFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelAdyenApiFlutter>());
  });

  test('getPlatformVersion', () async {
    AdyenApiFlutter adyenApiFlutterPlugin = AdyenApiFlutter();
    MockAdyenApiFlutterPlatform fakePlatform = MockAdyenApiFlutterPlatform();
    AdyenApiFlutterPlatform.instance = fakePlatform;

    expect(await adyenApiFlutterPlugin.getPlatformVersion(), '42');
  });
}
