# Music fidelity and Concert safety

Concert is now a subtle filtered stereo room rather than a loud mono echo. Dry left/right
channels remain intact. The room uses four short preallocated damped feedback delays,
a 180 Hz high-pass send and a normalised decay; bass and vocals are less masked.
Intensity is proportional to the effect and 0% is neutral in every spatial mode.

Tonal preset curves are unchanged: Flat, Bass boost, Vocal, Pop, Rock, Dance, Hip-hop,
Jazz, Classical and Treble boost. Headroom now includes the combined EQ/bass response
and spatial gain. These settings can sound quieter than the old overdriven versions;
use output volume for listening level. Loudness matching remains a separate feature.

No GPU, convolution or additional playback stream was added. Delay buffers are reused;
unused stages are skipped. These are implementation properties, not measured device
battery claims. Android Auto, Bluetooth, phone speakers and headphones still require
listening and performance checks on hardware.

Regression checks cover all ten presets with extra bass, high-level sine distortion,
0% neutrality, stereo separation, mode changes and stale tails. Tones establish linearity
and peak safety for the tested cases; they do not replace music listening tests or prove
intersample true-peak protection.

Preset headroom scans now run on one shared background worker, with latest-settings
coalescing and sample-rate validation. Playback keeps its last complete plan until the
new settings/headroom pair is ready; initial playback remains neutral while preparing.
The render callback no longer runs the response scan. Tests cover coalescing, stale route
results and equivalence with synchronous DSP preparation.

Non-finite source samples are replaced with silence before entering EQ, spatial delays
or normalisation, so one corrupt decoder sample cannot permanently poison playback.
Biquad state flushes inaudible subnormal tails to keep silent processing inexpensive.
Recovery is tested across every spatial mode.

## Detailed fidelity and idle-power audit

- The PCM callback has no periodic timer or separate playback stream. Disabled stages
  are skipped, Flat with normalisation off uses exact stereo bypass, and unchanged
  settings do not enqueue more preparation work.
- Added high-level multitone/impulse tests across every preset and spatial mode at
  44.1/48 kHz, including extra bass, output finiteness and complete tail retirement.
- The focused mRadio suite passes 17 tests. These establish the checked signal properties;
  they do not establish intersample true peaks or phone battery consumption.
- Compare the same station/volume/route with EQ off, tonal EQ and Concert using a
  controlled screen-off run on the actual experimental APK before making power claims.

Reference: https://android-developers.googleblog.com/2024/04/how-to-effectively-ab-test-power-consumption-for-your-android-app-features.html
