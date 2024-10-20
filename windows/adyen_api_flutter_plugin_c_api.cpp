#include "include/adyen_api_flutter/adyen_api_flutter_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "adyen_api_flutter_plugin.h"

void AdyenApiFlutterPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  adyen_api_flutter::AdyenApiFlutterPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
