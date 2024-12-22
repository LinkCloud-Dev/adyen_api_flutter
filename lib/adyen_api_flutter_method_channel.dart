import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'adyen_api_flutter_platform_interface.dart';
import 'helper.dart';

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
  Future<void> init(String ipAddress, int keyVersion, String keyIdentifier, String keyPassphrase, bool testEnvironment) async {
    await methodChannel.invokeMethod('init',
        {
          'ipAddress': ipAddress,
          'keyVersion': keyVersion,
          'keyIdentifier': keyIdentifier,
          'keyPassphrase': keyPassphrase,
          'testEnvironment': testEnvironment,
        }
    );
  }

  @override
  Future<Map<String, dynamic>> paymentRequest(double amount, String POIID, String saleID) async {
    final response = await methodChannel.invokeMethod('paymentRequest',
        {
          'amount': amount,
          'POIID': POIID,
          'saleID': saleID,
        }
    );
    return response;
  }

  @override
  Future<void> abortRequest(String POIID, String saleID) async {
    await methodChannel.invokeMethod('abortRequest',
        {
          'POIID': POIID,
          'saleID': saleID,
        }
    );
  }

  @override
  Future<Map<dynamic, dynamic>> refundRequest(String transactionID, String POIID, String saleID) async {
    final response = await methodChannel.invokeMethod('refundRequest',
        {
          'transactionID': transactionID,
          'POIID': POIID,
          'saleID': saleID,
        }
    );
    return response;
  }

  @override
  Future<Map<dynamic, dynamic>> statusRequest(String transactionServiceID, MessageCategoryType statusRequestType, String POIID, String saleID) async {
    final response = await methodChannel.invokeMethod('statusRequest',
        {
          'transactionServiceID': transactionServiceID,
          'statusRequestType': statusRequestType.toString().split('.').last,
          'POIID': POIID,
          'saleID': saleID,
        }
    );
    return response;
  }
}
