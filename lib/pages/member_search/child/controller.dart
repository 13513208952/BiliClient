import 'package:PiliPlus/grpc/bilibili/app/interfaces/v1.pb.dart'
    show SearchArchiveReply;
import 'package:PiliPlus/grpc/space.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/pages/common/common_list_controller.dart';
import 'package:PiliPlus/pages/member_search/controller.dart';
import 'package:fixnum/fixnum.dart' show Int64;

class MemberSearchChildController extends CommonListController {
  MemberSearchChildController(this.controller);

  final MemberSearchController controller;

  late final _ps = Int64(20);
  late final _midInt64 = Int64(int.parse(controller.mid));

  @override
  void checkIsEnd(int length) {
    final count = controller.counts.first;
    if (count != -1 && length >= count) {
      isEnd = true;
    }
  }

  @override
  List? getDataList(response) {
    SearchArchiveReply data = response;
    controller.counts.first = data.total.toInt();
    return data.archives;
  }

  @override
  Future<LoadingState> customGetData() {
    return SpaceGrpc.searchArchive(
      mid: _midInt64,
      pn: page,
      ps: _ps,
      keyword: controller.editingController.text,
    );
  }
}
