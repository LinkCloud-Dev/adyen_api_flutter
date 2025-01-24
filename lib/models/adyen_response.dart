import 'package:enum_to_string/enum_to_string.dart';

class AdyenResponse {
  AdyenResult? result;
  String? serviceID;
  String? POIID;
  String? saleID;
  AdyenTransaction? adyenTransaction;
  String? errorCondition;
  String? reversedAmount;
  List<PaymentReceipt>? paymentReceipts;

  AdyenResponse({
    this.result,
    this.serviceID,
    this.POIID,
    this.saleID,
    this.adyenTransaction,
    this.errorCondition,
    this.reversedAmount,
    this.paymentReceipts,
  });

  factory AdyenResponse.fromMap(Map<dynamic, dynamic> obj) {
    return AdyenResponse(
      result: EnumToString.fromString(AdyenResult.values, obj['result']),
      serviceID: obj['serviceID'],
      POIID: obj['POIID'],
      saleID: obj['saleID'],
      adyenTransaction: AdyenTransaction.fromMap(obj['transaction']),
      errorCondition: obj['errorCondition'],
      reversedAmount: obj['reversedAmount'],
      paymentReceipts: (obj['paymentReceipt'] as List<dynamic>?)
          ?.map((receipt) => PaymentReceipt.fromMap(receipt))
          .toList(),
    );
  }
}

class AdyenTransaction {
  String? transactionID;
  String? timeStamp;

  AdyenTransaction({
    this.transactionID,
    this.timeStamp,
  });

  factory AdyenTransaction.fromMap(Map<dynamic, dynamic> obj) {
    return AdyenTransaction(
      transactionID: obj['transactionID'],
      timeStamp: obj['timeStamp'],
    );
  }
}

class PaymentReceipt {
  bool? requiredSignatureFlag;
  String? documentQualifier;
  OutputContent? outputContent;

  PaymentReceipt({
    this.requiredSignatureFlag,
    this.documentQualifier,
    this.outputContent,
  });

  factory PaymentReceipt.fromMap(Map<dynamic, dynamic> obj) {
    return PaymentReceipt(
      requiredSignatureFlag: obj['requiredSignatureFlag'],
      documentQualifier: obj['documentQualifier'],
      outputContent: OutputContent.fromMap(obj['outputContent']),
    );
  }

  /// Parses a [PaymentReceipt] object into a human-readable receipt string.
  String parseReceipt() {
    final buffer = StringBuffer();
    final outputTexts = outputContent?.outputText ?? [];

    for (final outputText in outputTexts) {
      String? text = outputText.text;
      if (text != null) {
        List<String> ls = text.split('&');
        String line = "";
        for (String s in ls) {
          List<String> keyValue = s.split('=');
          String key = Uri.decodeComponent(keyValue[0]);
          String value = Uri.decodeComponent(keyValue[1]);
          if (key == "name") {
            line += value + (' ' * (20- value.length));
          } else if (key == "value") {
            line += (' ' * (20- value.length)) + value;
          } else if (key == "key") {
            if (value == "approved" ||
                value == "refused" ||
                value == "void" ||
                value == "merchantTitle" ||
                value == "retain" ||
                value == "thanks" ||
                value == "cardholderHeader") {
              line = _centerText(line.trim(), 40);
            }
          }
        }

        if (outputText.endOfLineFlag == true) {
          buffer.writeln(line);
        }
      }
    }

    return buffer.toString();
  }

  String _centerText(String text, int width) {
    int left = (width - text.length) ~/ 2; // left padding
    int right = width - text.length - left; // right padding

    return ' ' * left + text + ' ' * right;
  }
}

class OutputContent {
  String? outputFormat;
  List<OutputText>? outputText;

  OutputContent({
    this.outputFormat,
    this.outputText,
  });

  factory OutputContent.fromMap(Map<dynamic, dynamic> obj) {
    return OutputContent(
      outputFormat: obj['outputFormat'],
      outputText: (obj['outputText'] as List<dynamic>?)
          ?.map((text) => OutputText.fromMap(text))
          .toList(),
    );
  }
}

class OutputText {
  String? text;
  bool? endOfLineFlag;
  String? characterStyle;

  OutputText({
    this.text,
    this.endOfLineFlag,
    this.characterStyle,
  });

  factory OutputText.fromMap(Map<dynamic, dynamic> obj) {
    return OutputText(
      text: obj['text'],
      endOfLineFlag: obj['endOfLineFlag'],
      characterStyle: obj['characterStyle'],
    );
  }
}

enum AdyenResult { Success, Failure, Partial }