#ifndef FLUTTER_PLUGIN_ADYEN_API_FLUTTER_PLUGIN_H_
#define FLUTTER_PLUGIN_ADYEN_API_FLUTTER_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

namespace adyen_api_flutter {

class AdyenApiFlutterPlugin : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  AdyenApiFlutterPlugin();

  virtual ~AdyenApiFlutterPlugin();

  // Disallow copy and assign.
  AdyenApiFlutterPlugin(const AdyenApiFlutterPlugin&) = delete;
  AdyenApiFlutterPlugin& operator=(const AdyenApiFlutterPlugin&) = delete;

  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

}  // namespace adyen_api_flutter

#endif  // FLUTTER_PLUGIN_ADYEN_API_FLUTTER_PLUGIN_H_
