import 'package:flutter/services.dart';

enum AvailabilityStrategy {
  compatible('兼容策略'),
  normal('常规策略'),
  aggressive('激进策略');

  const AvailabilityStrategy(this.label);

  final String label;
}

typedef AvailabilitySupportStatus = ({
  bool enabled,
  int pending,
  int results,
  bool coolingDown,
  AvailabilityStrategy strategy,
  bool resourcePaused,
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
      strategy: AvailabilityStrategy.values.byName(
        (status['strategy'] as String).toLowerCase(),
      ),
      resourcePaused: status['resourcePaused'] as bool,
    );
  }

  static Future<void> setEnabled(bool enabled) async {
    await _channel.invokeMethod<void>('setEnabled', enabled);
  }

  static Future<void> setHangMode(bool enabled) =>
      _channel.invokeMethod<void>('setHangMode', enabled);

  static Future<void> setStrategy(AvailabilityStrategy strategy) =>
      _channel.invokeMethod<void>('setStrategy', strategy.name.toUpperCase());

  static Future<int?> importCandidates() =>
      _channel.invokeMethod<int>('importCandidates');

  static Future<int?> exportResults() =>
      _channel.invokeMethod<int>('exportResults');
}
