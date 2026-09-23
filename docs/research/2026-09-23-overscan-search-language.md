# Search language for TV and projector overscan

Research date: September 23, 2026.

Lead with **TV and projector overscan** and explain it as **cropped edges** or **edges cut off**. Add ordinary descriptions such as **zoomed in** and **resize the picture** where they help readers recognize the problem. These recommendations come from the wording below, not keyword-volume measurements or evidence of a ranking improvement.

## Evidence

Original user reports establish how people describe their symptoms. Official support pages establish the names of display settings. Neither establishes compatibility with this utility.

| Source | Observed language | Use in this repository |
| --- | --- | --- |
| [Reddit, "zooming/adjusting", December 16, 2022](https://www.reddit.com/r/Chromecast/comments/zn2dwc/) | The original poster says the Chromecast is "zoomed in" and the "picture is cut off around the edges". | Pair the technical word overscan with visible symptoms in the opening description. |
| [Reddit, projector image resizing question, January 8, 2023](https://www.reddit.com/r/Chromecast/comments/106vryz/) | A projector owner asks to "scale down the image" because content reaches the surrounding wall. | Use resize, scale down, and fit the screen. This report describes an oversized projection, so it is wording evidence, not a confirmed overscan diagnosis. |
| [Reddit, "CCwGTV overscan on apps", October 23, 2020](https://www.reddit.com/r/Chromecast/comments/jgr1n6/) | The original poster reports cropped app edges; another firsthand report says the "image is larger than the screen". | Describe missing app controls as well as cropped video. Screen too big is a useful plain-language paraphrase, not an exact phrase verified in this sample. |
| [Google Home & Nest Community, Chromecast scaling question, June 8, 2026](https://support.google.com/googlehome/thread/439931298/can-t-scale-chromecast-window-to-fit-my-tv?hl=en) | The original poster reports the "edge of everything cut off" and wants it to "fit your screen". | Include edges cut off and fit the screen. Use the original report only; the replies are not authoritative technical documentation. |
| [Google, display settings for Chromecast with Google TV and Google TV Streamer](https://support.google.com/chromecast/answer/10117046?hl=en) | The introduction uses "adjust the screen size and resolution". The page lists Resolution and Text scaling separately. | Adjust screen size is recognizable official language. Do not equate text scaling or resolution selection with whole-picture overscan correction. |
| [Apple, overscan and underscan on TVs and projectors](https://support.apple.com/en-ie/102202) | Apple explicitly covers both display types and describes overscan as picture margins hidden outside the screen. It lists zoom, aspect ratio, and screen fit among TV setting names. | Use a product name that covers both TVs and projectors. This is evidence for the problem's scope, not support for running this utility on Apple TV. |
| [Samsung, adjusting TV picture size](https://www.samsung.com/ae/support/tv-audio-video/adjust-the-picture-size-on-your-samsung-tv/) | Samsung calls its setting "Fit to Screen" and notes that availability depends on the input signal. | Mention Fit to Screen when suggesting the display's own controls as the first thing to check. Do not imply that every projector has this setting. |
| [NVIDIA SHIELD TV Pro support](https://www.nvidia.com/en-eu/shield/support/shield-tv-pro/) | NVIDIA documents an "Adjust for overscan" setting for displays without their own adjustment. | The problem affects Android TV boxes from other manufacturers too; use Android TV in the product name. This app has not been tested on SHIELD. |
| [Xiaomi, incomplete picture with a Xiaomi Box](https://www.mi.com/global/support/faq/details/KA-548390/) | Xiaomi describes a picture that does not fit completely and points to image zoom and position controls. | Explain the visible symptom across TV-box brands without claiming app compatibility. |

The Reddit excerpts were available in the search index, while direct page opening returned errors. Google Community's indexed page included the original post; its direct page returned only the site shell. The official Google, Apple, and Samsung pages were readable directly.

## Prioritized wording

1. Put **TV and projector overscan**, **Google TV**, and **cropped edges** in the title or first paragraph. Keep **Android TV 14** and the tested device nearby so that readers can assess compatibility immediately.
2. Use **edges cut off**, **zoomed in**, **picture larger than the screen**, **resize the picture**, and **fit the screen** in a short symptom section. Write normal sentences, not a keyword list.
3. Use **adjust screen size** and **overscan correction** when explaining calibration. Explain that the tool shrinks Android's composed picture inside the existing HDMI mode.
4. Address **Chromecast overscan** in a compatibility FAQ only. The search results show that Chromecast owners describe the same symptoms, but this utility has not been verified on Chromecast. Do not label it a Chromecast fix or add a Chromecast topic.

Keep the scope from the current [README](../../README.md): Android TV 14 only; verified on Google TV Streamer connected to ZEEMR Z1 Pro at 1920 x 1080. Other devices and Android versions are not part of that verified setup. Changing the HDMI resolution, projector placement, keystone, or aspect ratio is a different operation from the utility's picture scaling.

The original name Projector Calibrator understates the problem's scope. The reports above include televisions, and NVIDIA and Xiaomi document the problem on their own boxes. The chosen public name, Android TV Overscan, identifies the platform and problem without tying the product to a display type or one hardware manufacturer. The compatibility statement remains limited to the verified setup.

## Suggested GitHub metadata

Description, 137 characters:

> Fix overscan and cropped edges on TVs and projectors using Android TV 14. Adjust with your remote. Tested on Google TV Streamer at 1080p.

Topics:

`tv` `projector` `overscan` `android-tv` `android-tv-14` `google-tv` `google-tv-streamer` `screen-calibration` `adb`

These topics describe the project and its tested platform. The sources do not establish search traffic, relative keyword popularity, or guaranteed search placement.
