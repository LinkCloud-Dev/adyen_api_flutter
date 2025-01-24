import 'package:adyen_api_flutter/models/adyen_response.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:adyen_api_flutter/adyen_api_flutter.dart';
import 'package:adyen_api_flutter/helper.dart';

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

  // CLIENT SETUP
  // SecurityKey configs
  int keyVersion = 1;
  String keyIdentifier = "keyIdentifier";
  String keyPassphrase = "passphrase";

  String ipAddress = "192.168.0.118";

  bool testEnvironment = true;

  // REQUEST
  // Your unique ID for the POS system component to send this request from.
  String saleID = "002"; //"YOUR_CASH_REGISTER_ID"
  // 	The unique ID of the terminal to send this request to. Format: [device model]-[serial number].
  String POIID = "S1F2-000158234612430"; //"YOUR_TERMINAL_ID"
  double paymentAmount = 0.0;

  MessageCategoryType statusRequestType = MessageCategoryType.PAYMENT;

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
      platformVersion = await _adyenApiFlutterPlugin.getPlatformVersion() ??
          'Unknown platform version';
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
    // init returns
    // true if init success
    // false if init'd already
    // error if init failed
    var result = await _adyenApiFlutterPlugin.init(
        ipAddress, keyVersion, keyIdentifier, keyPassphrase, testEnvironment);
  }

  Future<void> _paymentRequest() async {
    // use without saleID (default to "001")
    final Map result = await _adyenApiFlutterPlugin.paymentRequest(
        paymentAmount, POIID);

    final response = AdyenResponse.fromMap(result);

    print(">> response from map: ${response.result.toString()}, ${response.serviceID}, "
        "${response.adyenTransaction?.transactionID}, ${response.adyenTransaction?.timeStamp},"
        "${response.errorCondition}");
    print(">> response parsed receipt: ${response.paymentReceipts![0].parseReceipt()}");
    print(">> response parsed receipt: ${response.paymentReceipts![1].parseReceipt()}");
    print(">> response string: ${result.toString()}");
    // use with saleID
    // var result = await _adyenApiFlutterPlugin.paymentRequest(toDoubleAmountTrimmed(amount), POIID, saleID: saleID);
  }

  Future<void> _abortRequest() async {
    // use without saleID (default to "001")
    var result = await _adyenApiFlutterPlugin.abortRequest(POIID);
    // use with saleID
    // var result = await _adyenApiFlutterPlugin.abortRequest(POIID, saleID: saleID);
  }

  final TextEditingController _refundIdController = TextEditingController();
  final TextEditingController _refundAmountController = TextEditingController();

  // input: transactionID
  Future<void> _refundRequest() async {
    var refundId = _refundIdController.text.trim();
    if (refundId.isEmpty) {
      throw Exception("Refund ID cannot be empty");
    }

    var refundAmountString = _refundAmountController.text.trim();
    double? refundAmount = refundAmountString == ""
        ? null : double.parse(refundAmountString);

    // use without saleID (default to "001")
    var result = await _adyenApiFlutterPlugin.refundRequest(refundId, POIID, refundAmount: refundAmount);

    final response = AdyenResponse.fromMap(result);

    print(">> response from map: ${response.result.toString()}, ${response.serviceID}, "
        "${response.adyenTransaction?.transactionID}, ${response.adyenTransaction?.timeStamp},"
        "${response.errorCondition}");

    print(">> response string: ${result.toString()}");
    // use with saleID
    // var result = await _adyenApiFlutterPlugin.abortRequest(POIID, saleID: saleID);
  }

  final TextEditingController _statusIdController = TextEditingController();

  // input: serviceID
  Future<void> _statusRequest() async {
    var statusId = _statusIdController.text.trim();
    if (statusId.isEmpty) {
      throw Exception("Status request ID cannot be empty");
    }

    // use without saleID (default to "001")
    var result = await _adyenApiFlutterPlugin.statusRequest(statusId, statusRequestType, POIID);
    print(">> flutter response: ${result.toString()}");
    // use with saleID
    // var result = await _adyenApiFlutterPlugin.statusRequest(POIID, saleID: saleID);
  }

  // converts string to double
  double toDoubleAmountTrimmed(String amount) {
    try {
      // Parse the string to a double
      double parsedAmount = double.parse(amount);

      // Trim the double to 2 decimal places
      return double.parse(parsedAmount.toStringAsFixed(2));
    } catch (e) {
      throw FormatException("Invalid amount format: $amount");
    }
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
              const SizedBox(height: 20),
              // Row of payment buttons
              Text(
                "Payment Amount: \$${paymentAmount.toStringAsFixed(2)}",
                style: const TextStyle(fontSize: 18),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        paymentAmount = 1.00;
                      });
                    },
                    child: const Text("\$1.00"),
                  ),
                  const SizedBox(width: 10), // Add spacing between buttons
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        paymentAmount = 2.99;
                      });
                    },
                    child: const Text("\$2.99"),
                  ),
                  const SizedBox(width: 10),
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        paymentAmount = 5.45;
                      });
                    },
                    child: const Text("\$5.45"),
                  ),
                  const SizedBox(width: 10),
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        paymentAmount = 11.85;
                      });
                    },
                    child: const Text("\$11.85"),
                  ),
                ],
              ),
              ElevatedButton(
                  onPressed: () => _paymentRequest(),
                  child: const Text("Payment Request")),
              const SizedBox(height: 20),
              ElevatedButton(
                  onPressed: () => _abortRequest(),
                  child: const Text("Abort Request")),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Padding(
                    padding: const EdgeInsets.all(8),
                    child: SizedBox(
                      width: 400, // Set your desired width
                      height: 50, // Optional: Set height if needed
                      child: TextField(
                        controller: _refundIdController,
                        decoration: const InputDecoration(
                          labelText: "Refund ID",
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8),
                    child: SizedBox(
                      width: 200, // Set your desired width
                      height: 50, // Optional: Set height if needed
                      child: TextField(
                        controller: _refundAmountController,
                        decoration: const InputDecoration(
                          labelText: "Refund Amount",
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
              ElevatedButton(
                  onPressed: () => _refundRequest(),
                  child: const Text("Refund Request")),
              Padding(
                padding: const EdgeInsets.all(8),
                child: SizedBox(
                  width: 400, // Set your desired width
                  height: 50, // Optional: Set height if needed
                  child: TextField(
                    controller: _statusIdController,
                    decoration: const InputDecoration(
                      labelText: "Transaction Service ID",
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        statusRequestType = MessageCategoryType.PAYMENT;
                      });
                    },
                    child: const Text("PAYMENT"),
                  ),
                  const SizedBox(width: 10), // Add spacing between buttons
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        statusRequestType = MessageCategoryType.REVERSAL;
                      });
                    },
                    child: const Text("REVERSAL"),
                  ),
                ],
              ),
              ElevatedButton(
                  onPressed: () => _statusRequest(),
                  child: const Text("Transaction Status Request")),
            ],
          ),
        ),
      ),
    );
  }
}
