import 'package:PiliPlus/common/constants.dart';
import 'package:PiliPlus/grpc/bilibili/main/community/reply/v1.pb.dart';
import 'package:PiliPlus/grpc/bilibili/pagination.pb.dart';
import 'package:PiliPlus/grpc/grpc_req.dart';
import 'package:PiliPlus/grpc/url.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/utils/reply_author_filter.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:fixnum/fixnum.dart';

abstract final class ReplyGrpc {
  static bool antiGoodsReply = Pref.antiGoodsReply;
  static RegExp replyRegExp = RegExp(
    Pref.banWordForReply,
    caseSensitive: false,
  );
  static bool enableFilter = replyRegExp.pattern.isNotEmpty;

  // static Future replyInfo({required int rpid}) {
  //   return _request(
  //     GrpcUrl.replyInfo,
  //     ReplyInfoReq(rpid: Int64(rpid)),
  //     ReplyInfoReply.fromBuffer,
  //     onSuccess: (response) => response.reply,
  //   );
  // }

  // ref BiliRoamingX
  static bool needRemoveGoodGrpc(ReplyInfo reply) {
    return (reply.content.urls.isNotEmpty &&
            reply.content.urls.values.any((url) {
              return url.hasExtra() &&
                  (url.extra.goodsCmControl == Int64.ONE ||
                      url.extra.hasGoodsItemId() ||
                      url.extra.hasGoodsPrefetchedCache());
            })) ||
        reply.content.message.contains(Constants.goodsUrlPrefix);
  }

  static bool needRemoveGrpc(ReplyInfo reply) {
    return (enableFilter && replyRegExp.hasMatch(reply.content.message)) ||
        (antiGoodsReply && needRemoveGoodGrpc(reply)) ||
        ReplyAuthorFilter.matchesNameOrLevel(reply);
  }

  static bool get _hasLocalFilters =>
      enableFilter || antiGoodsReply || ReplyAuthorFilter.hasNameOrLevelFilter;

  static void _filterReplies(
    List<ReplyInfo> replies,
    bool Function(ReplyInfo) matches,
  ) {
    replies.removeWhere((reply) {
      if (matches(reply)) return true;
      if (reply.replies.isNotEmpty) _filterReplies(reply.replies, matches);
      return false;
    });
  }

  static void _filterMainList(
    MainListReply response,
    bool Function(ReplyInfo) matches,
  ) {
    if (response.hasUpTop()) {
      final top = response.upTop;
      if (matches(top)) {
        response.clearUpTop();
      } else if (top.replies.isNotEmpty) {
        _filterReplies(top.replies, matches);
      }
    }
    if (response.replies.isNotEmpty) _filterReplies(response.replies, matches);
  }

  static Iterable<ReplyInfo> _mainListRoots(MainListReply response) sync* {
    if (response.hasUpTop()) yield response.upTop;
    yield* response.replies;
  }

  static Future<LoadingState<MainListReply>> mainList({
    int type = 1,
    required int oid,
    required Mode mode,
    required String? offset,
    required Int64? cursorNext,
  }) async {
    final res = await GrpcReq.request(
      GrpcUrl.mainList,
      MainListReq(
        oid: Int64(oid),
        type: Int64(type),
        rpid: Int64.ZERO,
        // cursor: CursorReq(
        //   mode: mode,
        //   next: cursorNext,
        // ),
        mode: mode,
        pagination: offset == null ? null : FeedPagination(offset: offset),
      ),
      MainListReply.fromBuffer,
    );
    if (res case Success(:final response)) {
      if (_hasLocalFilters) _filterMainList(response, needRemoveGrpc);
      if (ReplyAuthorFilter.hasRegistrationYearFilter) {
        final lookup = ReplyAuthorFilter.prepareRegistrationYears(
          _mainListRoots(response),
        );
        if (lookup != null) await lookup;
        _filterMainList(response, ReplyAuthorFilter.matchesRegistrationYear);
      }
    }
    return res;
  }

  static Future<LoadingState<DetailListReply>> detailList({
    int type = 1,
    required int oid,
    required int root,
    required int rpid,
    required Mode mode,
    required String? offset,
  }) async {
    final res = await GrpcReq.request(
      GrpcUrl.detailList,
      DetailListReq(
        oid: Int64(oid),
        type: Int64(type),
        root: Int64(root),
        rpid: Int64(rpid),
        scene: DetailListScene.REPLY,
        mode: mode,
        pagination: offset == null ? null : FeedPagination(offset: offset),
      ),
      DetailListReply.fromBuffer,
    );
    if (res case Success(:final response) when response.hasRoot()) {
      final root = response.root;
      if (_hasLocalFilters && root.replies.isNotEmpty) {
        _filterReplies(root.replies, needRemoveGrpc);
      }
      if (ReplyAuthorFilter.hasRegistrationYearFilter) {
        final lookup = ReplyAuthorFilter.prepareRegistrationYears([root]);
        if (lookup != null) await lookup;
        _filterReplies(root.replies, ReplyAuthorFilter.matchesRegistrationYear);
      }
    }
    return res;
  }

  static Future<LoadingState<DialogListReply>> dialogList({
    int type = 1,
    required int oid,
    required int root,
    required int dialog,
    required String? offset,
  }) async {
    final res = await GrpcReq.request(
      GrpcUrl.dialogList,
      DialogListReq(
        oid: Int64(oid),
        type: Int64(type),
        root: Int64(root),
        dialog: Int64(dialog),
        pagination: offset == null ? null : FeedPagination(offset: offset),
      ),
      DialogListReply.fromBuffer,
    );
    if (res case Success(:final response) when response.replies.isNotEmpty) {
      final replies = response.replies;
      if (_hasLocalFilters) _filterReplies(replies, needRemoveGrpc);
      if (ReplyAuthorFilter.hasRegistrationYearFilter) {
        final lookup = ReplyAuthorFilter.prepareRegistrationYears(replies);
        if (lookup != null) await lookup;
        _filterReplies(replies, ReplyAuthorFilter.matchesRegistrationYear);
      }
    }
    return res;
  }

  static Future<LoadingState<SearchItemReply>> searchItem({
    required int page,
    required SearchItemType itemType,
    required int oid,
    int type = 1,
    String? keyword,
  }) {
    return GrpcReq.request(
      GrpcUrl.searchItem,
      SearchItemReq(
        cursor: SearchItemCursorReq(next: Int64(page), itemType: itemType),
        oid: Int64(oid),
        type: Int64(type),
        keyword: keyword,
      ),
      SearchItemReply.fromBuffer,
    );
  }

  static Future<LoadingState<TranslateReplyResp>> translateReply({
    required Int64 type,
    required Int64 oid,
    required Int64 rpid,
  }) {
    return GrpcReq.request(
      GrpcUrl.translateReply,
      TranslateReplyReq(type: type, oid: oid, rpids: [rpid]),
      TranslateReplyResp.fromBuffer,
    );
  }
}
