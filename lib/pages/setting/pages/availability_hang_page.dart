import 'dart:async';
import 'dart:math' as math;

import 'package:PiliPlus/services/availability_support_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AvailabilityHangPage extends StatefulWidget {
  const AvailabilityHangPage({super.key});

  @override
  State<AvailabilityHangPage> createState() => _AvailabilityHangPageState();
}

class _AvailabilityHangPageState extends State<AvailabilityHangPage>
    with SingleTickerProviderStateMixin {
  late final AnimationController _drift;
  late final Timer _refreshTimer;
  AvailabilitySupportStatus? _status;
  bool _reading = false;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _drift = AnimationController(
      vsync: this,
      duration: const Duration(minutes: 5),
    )..repeat();
    unawaited(_refresh());
    _refreshTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      unawaited(_refresh());
    });
  }

  @override
  void dispose() {
    _refreshTimer.cancel();
    _drift.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    if (_reading) return;
    _reading = true;
    try {
      final status = await AvailabilitySupportService.status();
      if (mounted && (status != _status || _failed)) {
        setState(() {
          _status = status;
          _failed = false;
        });
      }
    } on PlatformException {
      if (mounted && !_failed) setState(() => _failed = true);
    } finally {
      _reading = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    final total = (status?.pending ?? 0) + (status?.results ?? 0);
    final done = status?.results ?? 0;
    final fraction = total == 0 ? 0.0 : done / total;
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 26, vertical: 38),
          child: AnimatedBuilder(
            animation: _drift,
            builder: (context, child) {
              final phase = _drift.value * 2 * math.pi;
              return Align(
                alignment: Alignment(
                  math.sin(phase) * .95,
                  math.sin(phase * 2) * .86,
                ),
                child: child,
              );
            },
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  status == null ? '—' : '${(fraction * 100).toStringAsFixed(2)}%',
                  style: const TextStyle(
                    color: Color(0xff535350),
                    fontSize: 29,
                    letterSpacing: 1.4,
                    fontFeatures: [FontFeature.tabularFigures()],
                  ),
                ),
                const SizedBox(height: 7),
                SizedBox(
                  width: 134,
                  child: LinearProgressIndicator(
                    value: fraction,
                    minHeight: 2,
                    color: const Color(0xff414140),
                    backgroundColor: const Color(0xff171716),
                    semanticsLabel: '验证进度',
                  ),
                ),
                const SizedBox(height: 7),
                Text(
                  _failed ? '暂时无法读取进度' : status == null ? '读取中' : '$done / $total',
                  style: const TextStyle(
                    color: Color(0xff444442),
                    fontSize: 13,
                    fontFeatures: [FontFeature.tabularFigures()],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
