import 'package:enum_to_string/enum_to_string.dart';

class AdyenResponse {
  AdyenResult? result;
  String? serviceID;
  String? POIID;
  String? saleID;
  AdyenTransaction? adyenTransaction;
  String? errorCondition;
  String? reversedAmount;

  AdyenResponse({
    this.result,
    this.serviceID,
    this.POIID,
    this.saleID,
    this.adyenTransaction,
    this.errorCondition,
    this.reversedAmount,
  });

  factory AdyenResponse.fromMap(Map<dynamic, dynamic> obj) {

    return AdyenResponse(
      result: EnumToString.fromString(AdyenResult.values, obj['result'])!,
      serviceID: obj['serviceID'],
      POIID: obj['POIID'],
      saleID: obj['saleID'],
      adyenTransaction: AdyenTransaction.fromMap(obj['transaction']),
      errorCondition: obj['errorCondition'],
      reversedAmount: obj['reversedAmount'],
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

enum AdyenResult { Success, Failure, Partial }