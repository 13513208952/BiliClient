import 'package:flutter/services.dart';

typedef AvailabilitySupportStatus = ({
  bool enabled,
  int pending,
  int results,
  bool coolingDown,
});

abstract final class AvailabilitySupportService {
  static const _channel = MethodChannel(
    'com.bilibilitakemeback/availability-support',
  );

  static Future<AvailabilitySupportStatus> status() async {
    final status = (await _channel.invokeMapMethod<String, Object?>(
      'status',
    ))!;
    return (
      enabled: status['enabled'] as bool,
      pending: status['pending'] as int,
      results: status['results'] as int,
      coolingDown: status['coolingDown'] as bool,
    );
  }

  static Future<void> setEnabled(bool enabled) async {
    await _channel.invokeMethod<void>('setEnabled', enabled);
  }

  static Future<void> setHangMode(bool enabled) =>
      _channel.invokeMethod<void>('setHangMode', enabled);

  static Future<int?> importCandidates() =>
      _channel.invokeMethod<int>('importCandidates');

  static Future<int?> exportResults() =>
      _channel.invokeMethod<int>('exportResults');
}
