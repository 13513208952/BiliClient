import 'package:PiliPlus/models/common/enum_with_label.dart';
import 'package:PiliPlus/pages/common/common_controller.dart';
import 'package:PiliPlus/pages/nostalgia/controller.dart';
import 'package:PiliPlus/pages/rcmd/controller.dart';
import 'package:PiliPlus/pages/rcmd/view.dart';
import 'package:PiliPlus/utils/extension/get_ext.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

enum HomeTabType implements EnumWithLabel {
  rcmd('推荐'),
  nostalgia('怀旧推荐');

  @override
  final String label;
  const HomeTabType(this.label);

  ScrollOrRefreshMixin Function() get ctr => switch (this) {
    HomeTabType.rcmd => Get.find<RcmdController>,
    HomeTabType.nostalgia => Get.find<NostalgiaController>,
  };

  Widget get page => switch (this) {
    HomeTabType.rcmd => const RcmdPage(key: ValueKey('homeTab-rcmd')),
    HomeTabType.nostalgia => RcmdPage(
      key: const ValueKey('homeTab-nostalgia'),
      controllerBuilder: () => Get.putOrFind(NostalgiaController.new),
    ),
  };
}
