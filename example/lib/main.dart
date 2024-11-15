import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:adyen_api_flutter/adyen_api_flutter.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _adyenApiFlutterPlugin = AdyenApiFlutter();

  // Your unique ID for the POS system component to send this request from.
  String saleID = "001"; //"YOUR_CASH_REGISTER_ID"
  // 	The unique ID of the terminal to send this request to. Format: [device model]-[serial number].
  String POIID = "S1F2-000158234612430"; //"YOUR_TERMINAL_ID"
  String amount = "1.99";

  // SecurityKey configs
  int keyVersion = 1;
  String keyIdentifier = "keyIdentifier";
  String keyPassphrase = "passphrase";

  String ipAddress = "192.168.0.118";

  bool testEnvironment = true;


  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await _adyenApiFlutterPlugin.getPlatformVersion() ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  Future<void> _init() async {
    var result = await _adyenApiFlutterPlugin.init(ipAddress, keyVersion, keyIdentifier, keyPassphrase, testEnvironment);
  }

  Future<void> _paymentRequest() async {
    var result = await _adyenApiFlutterPlugin.request();
  }

  // TODO: UI for terminal ip address, encryption key (identifier, passphrase and version)

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            children: [
              Text('Running on: $_platformVersion\n'),
              ElevatedButton(
                  onPressed: () => _init(), child: const Text("Init")),
              ElevatedButton(
                  onPressed: () => _paymentRequest(), child: const Text("Payment Request")),
            ],
          ),
        ),
      ),
    );
  }
}
