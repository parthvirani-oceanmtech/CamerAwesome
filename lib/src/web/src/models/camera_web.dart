import 'dart:html' as html;

import 'package:camerawesome/src/web/src/models/camera_options.dart';
import 'package:camerawesome/src/web/src/shims/dart_ui.dart' as ui;

String _getViewType(int cameraId) => 'plugins.flutter.io/camera_$cameraId';

class CameraWeb {
  /// Creates a new instance of [Camera]
  /// with the given [textureId] and optional
  /// [options] and [window].
  CameraWeb({
    required this.textureId,
    this.options = const CameraOptions(),
  });

  /// The texture id used to register the camera view.
  final int textureId;

  /// The camera options used to initialize a camera, empty by default.
  final CameraOptions options;

  /// The video element that displays the camera stream.
  /// Initialized in [initialize].
  late final html.VideoElement videoElement;

  /// The wrapping element for the [videoElement] to avoid overriding
  /// the custom styles applied in [_applyDefaultVideoStyles].
  /// Initialized in [initialize].
  late final html.DivElement divElement;

  /// The camera stream displayed in the [videoElement].
  /// Initialized in [initialize] and [play], reset in [stop].
  html.MediaStream? stream;

  /// Initializes the camera stream displayed in the [videoElement].
  /// Registers the camera view with [textureId] under [_getViewType] type.
  /// Emits the camera default video track on the [onEnded] stream when it ends.
  Future<void> initialize(final html.MediaStream mediaStream) async {
    stream = mediaStream;
    videoElement = html.VideoElement();

    divElement = html.DivElement()
      ..style.setProperty('object-fit', 'cover')
      ..append(videoElement);

    ui.platformViewRegistry.registerViewFactory(
      _getViewType(textureId),
      (_) => divElement,
    );

    videoElement
      ..autoplay = false
      ..muted = true
      ..srcObject = stream
      ..setAttribute('playsinline', '');

    _applyDefaultVideoStyles(videoElement);
  }

  /// Applies default styles to the video [element].
  void _applyDefaultVideoStyles(html.VideoElement element) {
    // final bool isBackCamera = getLensDirection() == CameraLensDirection.back;

    // // Flip the video horizontally if it is not taken from a back camera.
    // if (!isBackCamera) {
    //   element.style.transform = 'scaleX(-1)';
    // }

    element.style
      ..transformOrigin = 'center'
      ..pointerEvents = 'none'
      ..width = '100%'
      ..height = '100%'
      ..objectFit = 'cover';
  }

  String getViewType() => _getViewType(textureId);

  Future<void> play() {
    return videoElement.play();
  }
}
