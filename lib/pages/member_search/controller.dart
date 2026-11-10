import 'package:PiliPlus/pages/member_search/child/controller.dart';
import 'package:PiliPlus/utils/extension/scroll_controller_ext.dart';
import 'package:PiliPlus/utils/utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class MemberSearchController extends GetxController {
  late final FocusNode focusNode;
  late final TextEditingController editingController;

  final mid = Get.parameters['mid']!;
  final uname = Get.parameters['uname'];

  final RxBool hasData = false.obs;
  final RxList<int> counts = <int>[-1].obs;

  late final MemberSearchChildController arcCtr;

  @override
  void onInit() {
    super.onInit();
    focusNode = FocusNode();
    editingController = TextEditingController();
    arcCtr = Get.put(
      MemberSearchChildController(this),
      tag: Utils.generateRandomString(8),
    );
  }

  void onClear() {
    if (editingController.value.text.isNotEmpty) {
      editingController.clear();
      counts.value = <int>[-1];
      hasData.value = false;
      focusNode.requestFocus();
    } else {
      Get.back();
    }
  }

  void submit() {
    if (editingController.text.isNotEmpty) {
      hasData.value = true;
      arcCtr
        ..scrollController.jumpToTop()
        ..onReload();
    }
  }

  @override
  void onClose() {
    focusNode.dispose();
    editingController.dispose();
    super.onClose();
  }
}
