
in vec2 TexCoord;

out vec4 fragColor;

uniform sampler2D gDepthMap;
// inverted model view matrix and projection matrix
uniform mat4 gInvMvmProj;

uniform float fogScale;
uniform float fogVerticalScale;
uniform float nearFogStart;
uniform float nearFogLength;
uniform int fullFogMode;

uniform vec4 fogColor;


/* ========MARCO DEFINED BY RUNTIME CODE GEN=========

float farFogStart;
float farFogLength;
float farFogMin;
float farFogRange;
float farFogDensity;

float heightFogStart;
float heightFogLength;
float heightFogMin;
float heightFogRange;
float heightFogDensity;
*/

// method definitions
// ==== The below 5 methods will be run-time generated. ====
float getNearFogThickness(float dist);
float getFarFogThickness(float dist);
float getHeightFogThickness(float dist);
float calculateFarFogDepth(float horizontal, float dist, float nearFogStart);
float calculateHeightFogDepth(float vertical, float realY);
float mixFogThickness(float near, float far, float height);
// =========================================================


const vec3 MAGIC = vec3(0.06711056, 0.00583715, 52.9829189);

float InterleavedGradientNoise(const in vec2 pixel) {
    float x = dot(pixel, MAGIC.xy);
    return fract(MAGIC.z * fract(x));
}

vec3 calcViewPosition(float fragmentDepth) {
    vec4 ndc = vec4(TexCoord.xy, fragmentDepth, 1.0);
    ndc.xyz = ndc.xyz * 2.0 - 1.0;

    vec4 eyeCoord = gInvMvmProj * ndc;
    return eyeCoord.xyz / eyeCoord.w;
}

/**
 * Fragment shader for fog.
 * This should be passed last so it applies above other affects like AO
 *
 * version: 2023-6-21
 */
void main() 
{
    float vertexYPos = 100.0f;
    float fragmentDepth = texture(gDepthMap, TexCoord).r;
    fragColor = vec4(fogColor.rgb, 0.0);

    // a fragment depth of "1" means the fragment wasn't drawn to,
    // we only want to apply Fog to LODs, not to the sky outside the LODs
    if (fragmentDepth < 1.0) {
        if (fullFogMode == 0) {
            // render fog based on distance from the camera
            vec3 vertexWorldPos = calcViewPosition(fragmentDepth);

            float horizontalDist = length(vertexWorldPos.xz) * fogScale;
            float heightDist = calculateHeightFogDepth(vertexWorldPos.y, vertexYPos) * fogVerticalScale;
            float farDist = calculateFarFogDepth(horizontalDist, length(vertexWorldPos.xyz) * fogScale, nearFogStart);

            float nearFogThickness = getNearFogThickness(horizontalDist);
            float farFogThickness = getFarFogThickness(farDist);
            float heightFogThickness = getHeightFogThickness(heightDist);
            float mixedFogThickness = mixFogThickness(nearFogThickness, farFogThickness, heightFogThickness);
            fragColor.a = clamp(mixedFogThickness, 0.0, 1.0);

            float dither = InterleavedGradientNoise(gl_FragCoord.xy) - 0.5;
            fragColor.a += dither / 255.0;
        }
        else if (fullFogMode == 1) {
            // render everything with the fog color
            fragColor.a = 1.0;
        }
        else {
            // test code.

            // this can be fired by manually changing the fullFogMode to a (normally)
            // invalid value (like 7). By having a separate if statement defined by
            // a uniform we don't have to worry about GLSL optimizing away different
            // options when testing, causing a bunch of headaches if we just want to render the screen red.

            float depthValue = textureLod(gDepthMap, TexCoord, 0).r;
            fragColor.rgb = vec3(depthValue); // Convert depth value to grayscale color
            fragColor.a = 1.0;
        }
    }
}

// Are these still needed?
float linearFog(float x, float fogStart, float fogLength, float fogMin, float fogRange) {
    x = clamp((x-fogStart)/fogLength, 0.0, 1.0);
    return fogMin + fogRange * x;
}

float exponentialFog(float x, float fogStart, float fogLength,
    float fogMin, float fogRange, float fogDensity)
{
    x = max((x-fogStart)/fogLength, 0.0) * fogDensity;
    return fogMin + fogRange - fogRange/exp(x);
}

float exponentialSquaredFog(float x, float fogStart, float fogLength,
    float fogMin, float fogRange, float fogDensity)
{
    x = max((x-fogStart)/fogLength, 0.0) * fogDensity;
    return fogMin + fogRange - fogRange/exp(x*x);
}
