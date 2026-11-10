import 'dart:math';

import 'package:PiliPlus/common/widgets/fractionally_sized_box.dart';
import 'package:PiliPlus/common/widgets/image_viewer/gallery_viewer.dart';
import 'package:PiliPlus/common/widgets/image_viewer/hero_dialog_route.dart';
import 'package:PiliPlus/grpc/im.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/http/search.dart';
import 'package:PiliPlus/models/common/image_preview_type.dart';
import 'package:PiliPlus/models/common/video/video_type.dart';
import 'package:PiliPlus/models_new/pgc/pgc_info_model/episode.dart';
import 'package:PiliPlus/models_new/video/video_detail/dimension.dart';
import 'package:PiliPlus/pages/common/common_intro_controller.dart';
import 'package:PiliPlus/pages/common/publish/publish_route.dart';
import 'package:PiliPlus/pages/contact/view.dart';
import 'package:PiliPlus/pages/fav_panel/view.dart';
import 'package:PiliPlus/pages/share/view.dart';
import 'package:PiliPlus/utils/android/android_helper.dart';
import 'package:PiliPlus/utils/app_scheme.dart';
import 'package:PiliPlus/utils/extension/context_ext.dart';
import 'package:PiliPlus/utils/extension/size_ext.dart';
import 'package:PiliPlus/utils/global_data.dart';
import 'package:PiliPlus/utils/id_utils.dart';
import 'package:PiliPlus/utils/platform_utils.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:PiliPlus/utils/utils.dart';
import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:url_launcher/url_launcher.dart';

abstract final class PageUtils {
  static RelativeRect menuPosition(Offset offset) {
    return .fromLTRB(offset.dx, offset.dy, offset.dx, 0);
  }

  static Future<void> imageView({
    int initialPage = 0,
    required List<SourceModel> imgList,
    int? quality,
    ValueChanged<int>? onPageChanged,
    String tag = '',
  }) {
    return Get.key.currentState!.push<void>(
      HeroDialogRoute(
        pageBuilder: (context, animation, secondaryAnimation) => GalleryViewer(
          sources: imgList,
          initIndex: initialPage,
          quality: quality ?? GlobalData().imgQuality,
          onPageChanged: onPageChanged,
          tag: tag,
        ),
      ),
    );
  }

  static Future<void> pmShare(
    BuildContext context, {
    required Map content,
  }) async {
    // if (kDebugMode) debugPrint(content.toString());

    List<UserModel> userList = <UserModel>[];

    final res = await ImGrpc.shareList(size: 5);
    if (res case Success(:final response)) {
      if (response.sessionList.isNotEmpty) {
        userList.addAll(
          response.sessionList.map<UserModel>(
            (item) => UserModel(
              mid: item.talkerId.toInt(),
              name: item.talkerUname,
              avatar: item.talkerIcon,
            ),
          ),
        );
      }
    }

    if (userList.isEmpty && context.mounted) {
      final UserModel? userModel = await Navigator.of(
        context,
      ).push(GetPageRoute(page: () => const ContactPage()));
      if (userModel != null) {
        userList.add(userModel);
      }
    }

    if (context.mounted) {
      showModalBottomSheet(
        context: context,
        builder: (context) => SharePanel(content: content, userList: userList),
        useSafeArea: true,
        enableDrag: false,
        isScrollControlled: true,
      );
    }
  }

  static void showFavBottomSheet({
    required BuildContext context,
    required FavMixin ctr,
  }) {
    showModalBottomSheet(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      constraints: BoxConstraints(
        maxWidth: min(640, context.mediaQueryShortestSide),
      ),
      builder: (BuildContext context) {
        final maxChildSize =
            PlatformUtils.isMobile && !context.mediaQuerySize.isPortrait
            ? 1.0
            : 0.7;
        return DraggableScrollableSheet(
          minChildSize: 0,
          maxChildSize: 1,
          snap: true,
          expand: false,
          snapSizes: [maxChildSize],
          initialChildSize: maxChildSize,
          builder: (BuildContext context, ScrollController scrollController) {
            return FavPanel(ctr: ctr, scrollController: scrollController);
          },
        );
      },
    );
  }

  static void reportVideo(int aid) {
    Get.toNamed(
      '/webview',
      parameters: {'url': 'https://www.bilibili.com/appeal/?avid=$aid'},
    );
  }

  static bool _fitsInAndroidRequirements(int width, int height) {
    final aspectRatio = width / height;
    const min = 1 / 2.39;
    const max = 2.39;
    return (min <= aspectRatio) && (aspectRatio <= max);
  }

  static void enterPip({
    int? width,
    int? height,
    bool autoEnter = false,
    required bool isLive,
    required bool isPlaying,
  }) {
    if (width != null &&
        height != null &&
        !_fitsInAndroidRequirements(width, height)) {
      if (height > width) {
        width = 9;
        height = 16;
      } else {
        width = 16;
        height = 9;
      }
    }
    PiliAndroidHelper.enterPip(
      width ?? 16,
      height ?? 9,
      autoEnter: autoEnter,
      isLive: isLive,
      isPlaying: isPlaying,
    );
  }

  static void onHorizontalPreviewState(
    ScaffoldState state,
    List<SourceModel> imgList,
    int index,
  ) {
    state.showBottomSheet(
      constraints: const BoxConstraints(),
      (context) => GalleryViewer(
        sources: imgList,
        initIndex: index,
        quality: GlobalData().imgQuality,
      ),
      enableDrag: false,
      elevation: 0.0,
      backgroundColor: Colors.transparent,
      sheetAnimationStyle: AnimationStyle.noAnimation,
    );
  }

  static void inAppWebview(String url, {bool off = false}) {
    if (Pref.openInBrowser) {
      launchURL(url);
    } else {
      if (off) {
        Get.offNamed(
          '/webview',
          parameters: {'url': url},
          arguments: {'inApp': true},
        );
      } else {
        Get.toNamed(
          '/webview',
          parameters: {'url': url},
          arguments: {'inApp': true},
        );
      }
    }
  }

  static Future<void> launchURL(
    String url, {
    LaunchMode mode = LaunchMode.externalApplication,
  }) async {
    try {
      final Uri uri = Uri.parse(url);
      if (!await launchUrl(uri, mode: mode)) {
        SmartDialog.showToast('Could not launch $url');
      }
    } catch (e) {
      SmartDialog.showToast(e.toString());
    }
  }

  static Future<void> handleWebview(
    String url, {
    bool off = false,
    bool inApp = false,
    Map? parameters,
  }) async {
    if (!inApp && Pref.openInBrowser) {
      if (!await PiliScheme.routePushFromUrl(url, selfHandle: true)) {
        launchURL(url);
      }
    } else {
      if (off) {
        Get.offNamed('/webview', parameters: {'url': url, ...?parameters});
      } else {
        PiliScheme.routePushFromUrl(url, parameters: parameters);
      }
    }
  }

  static Future<void>? showVideoBottomSheet(
    BuildContext context, {
    required Widget child,
    ValueGetter<EdgeInsets>? padding,
    double maxWidth = 500,
  }) {
    if (!context.mounted) {
      return null;
    }
    return Get.key.currentState!.push(
      PublishRoute(
        pageBuilder: (context, animation, secondaryAnimation) {
          final isPortrait = context.isPortrait;
          return SafeArea(
            child: CustomFractionallySizedBox(
              maxWidth: maxWidth,
              widthFactor: isPortrait ? 1.0 : 0.5,
              heightFactor: isPortrait ? 0.7 : 1.0,
              alignment: isPortrait ? .bottomCenter : .centerRight,
              child: Padding(
                padding: isPortrait ? padding?.call() ?? .zero : .zero,
                child: child,
              ),
            ),
          );
        },
        transitionDuration: const Duration(milliseconds: 350),
        transitionBuilder: (context, animation, secondaryAnimation, child) {
          final begin = context.isPortrait
              ? const Offset(0.0, 1.0)
              : const Offset(1.0, 0.0);
          return SlideTransition(
            position: animation.drive(
              Tween<Offset>(
                begin: begin,
                end: Offset.zero,
              ).chain(CurveTween(curve: Curves.easeInOut)),
            ),
            child: child,
          );
        },
        settings: RouteSettings(arguments: Get.arguments),
      ),
    );
  }

  static void toLiveRoom(int? roomId, {bool off = false}) {
    if (roomId == null) {
      return;
    }
    if (off) {
      Get.offNamed('/liveRoom', arguments: roomId);
    } else {
      PageUtils.toDupNamed('/liveRoom', arguments: roomId);
    }
  }

  static Future<void>? toVideoPage({
    VideoType videoType = VideoType.ugc,
    int? aid,
    String? bvid,
    required int cid,
    int? seasonId,
    int? epId,
    int? pgcType,
    String? cover,
    String? title,
    int? progress, // milliseconds
    Map? extraArguments,
    bool off = false,
    bool isVertical = false,
    Dimension? dimension,
  }) {
    final arguments = {
      'aid': aid ?? IdUtils.bv2av(bvid!),
      'bvid': bvid ?? IdUtils.av2bv(aid!),
      'cid': cid,
      'seasonId': ?seasonId,
      'epId': ?epId,
      'pgcType': ?pgcType,
      'cover': ?cover,
      'title': ?title,
      'progress': ?progress,
      'videoType': videoType,
      'isVertical': dimension?.isVertical ?? isVertical,
      'heroTag': Utils.makeHeroTag(cid),
      ...?extraArguments,
    };
    if (off) {
      return Get.offNamed(
        '/videoV',
        arguments: arguments,
        preventDuplicates: false,
      );
    } else {
      return Get.toNamed(
        '/videoV',
        arguments: arguments,
        preventDuplicates: false,
      );
    }
  }

  static final _pgcRegex = RegExp(r'(ep|ss)(\d+)');
  static bool viewPgcFromUri(
    String uri, {
    bool isPgc = true,
    int? progress, // milliseconds
    int? aid,
    bool off = false,
  }) {
    RegExpMatch? match = _pgcRegex.firstMatch(uri);
    if (match != null) {
      bool isSeason = match.group(1) == 'ss';
      String id = match.group(2)!;
      if (isPgc) {
        viewPgc(
          seasonId: isSeason ? id : null,
          epId: isSeason ? null : id,
          progress: progress,
          off: off,
        );
      } else {
        viewPugv(
          seasonId: isSeason ? id : null,
          epId: isSeason ? null : id,
          aid: aid,
          off: off,
        );
      }
      return true;
    }
    return false;
  }

  static EpisodeItem findEpisode(
    List<EpisodeItem> episodes, {
    dynamic epId,
    bool isPgc = true,
  }) {
    // epId episode -> progress episode -> first episode
    EpisodeItem? episode;
    if (epId != null) {
      epId = epId.toString();
      episode = episodes.firstWhereOrNull(
        (item) => (isPgc ? item.epId : item.id).toString() == epId,
      );
    }
    return episode ?? episodes.first;
  }

  static Future<void> viewPgc({
    dynamic seasonId,
    dynamic epId,
    int? progress, // milliseconds
    bool off = false,
  }) async {
    try {
      SmartDialog.showLoading(msg: '资源获取中');
      final res = await SearchHttp.pgcInfo(seasonId: seasonId, epId: epId);
      SmartDialog.dismiss();
      if (res case Success(:final response)) {
        final episodes = response.episodes;
        final hasEpisode = episodes != null && episodes.isNotEmpty;

        EpisodeItem? episode;

        void viewSection(EpisodeItem episode) {
          toVideoPage(
            videoType: VideoType.ugc,
            bvid: episode.bvid!,
            cid: episode.cid!,
            seasonId: response.seasonId,
            epId: episode.epId,
            cover: episode.cover,
            progress: progress,
            extraArguments: {'pgcApi': true, 'pgcItem': response},
            off: off,
          );
        }

        if (epId != null) {
          epId = epId.toString();
          if (hasEpisode) {
            episode = episodes.firstWhereOrNull(
              (item) => item.epId.toString() == epId,
            );
          }

          // find section
          if (episode == null) {
            final sections = response.section;
            if (sections != null && sections.isNotEmpty) {
              for (final section in sections) {
                final episodes = section.episodes;
                if (episodes != null && episodes.isNotEmpty) {
                  for (final episode in episodes) {
                    if (episode.epId.toString() == epId) {
                      // view as ugc
                      viewSection(episode);
                      return;
                    }
                  }
                }
              }
            }
          }
        }

        if (hasEpisode) {
          episode ??= findEpisode(
            episodes,
            epId: response.userStatus?.progress?.lastEpId,
          );
          toVideoPage(
            videoType: VideoType.pgc,
            bvid: episode.bvid!,
            cid: episode.cid!,
            seasonId: response.seasonId,
            epId: episode.epId,
            pgcType: response.type,
            cover: episode.cover,
            progress: progress,
            extraArguments: {'pgcItem': response},
            off: off,
          );
          return;
        } else {
          episode ??= response.section?.firstOrNull?.episodes?.firstOrNull;
          if (episode != null) {
            viewSection(episode);
            return;
          }
        }

        SmartDialog.showToast('资源加载失败');
      } else {
        res.toast();
      }
    } catch (e) {
      SmartDialog.dismiss();
      SmartDialog.showToast('$e');
      if (kDebugMode) debugPrint('$e');
    }
  }

  static Future<void> viewPugv({
    dynamic seasonId,
    dynamic epId,
    int? aid,
    bool off = false,
  }) async {
    try {
      SmartDialog.showLoading(msg: '资源获取中');
      final res = await SearchHttp.pugvInfo(seasonId: seasonId, epId: epId);
      SmartDialog.dismiss();
      if (res case Success(:final response)) {
        final episodes = response.episodes;
        if (episodes != null && episodes.isNotEmpty) {
          EpisodeItem? episode;
          if (aid != null) {
            episode = episodes.firstWhereOrNull((e) => e.aid == aid);
          }
          episode ??= findEpisode(
            episodes,
            epId: epId ?? response.userStatus?.progress?.lastEpId,
            isPgc: false,
          );
          toVideoPage(
            videoType: VideoType.pugv,
            aid: episode.aid!,
            cid: episode.cid!,
            seasonId: response.seasonId,
            epId: episode.id,
            cover: episode.cover,
            extraArguments: {'pgcItem': response},
            off: off,
          );
        } else {
          SmartDialog.showToast('资源加载失败');
        }
      } else {
        res.toast();
      }
    } catch (e) {
      SmartDialog.dismiss();
      SmartDialog.showToast(e.toString());
    }
  }

  static void toDupNamed(
    String page, {
    dynamic arguments,
    Map<String, String>? parameters,
    bool off = false,
  }) {
    if (off) {
      Get.offNamed(
        page,
        arguments: arguments,
        parameters: parameters,
        preventDuplicates: false,
      );
    } else {
      Get.toNamed(
        page,
        arguments: arguments,
        parameters: parameters,
        preventDuplicates: false,
      );
    }
  }
}
