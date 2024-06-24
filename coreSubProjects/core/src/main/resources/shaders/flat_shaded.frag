#version 150

in vec4 vertexColor;
in vec3 vertexWorldPos;
//in float vertexYPos;
in vec4 vPos;

out vec4 fragColor;


// Fog/Clip Uniforms
uniform float clipDistance = 0.0;

// Noise Uniforms
uniform bool noiseEnabled;
uniform int noiseSteps;
uniform float noiseIntensity;
uniform int noiseDropoff;


// The random functions for diffrent dimentions
float rand(float co) { return fract(sin(co*(91.3458)) * 47453.5453); }
float rand(vec2 co) { return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453); }
float rand(vec3 co) { return rand(co.xy + rand(co.z)); }

// Puts steps in a float
// EG. setting stepSize to 4 then this would be the result of this function
// In:  0.0, 0.1, 0.2, 0.3,  0.4,  0.5, 0.6, ..., 1.1, 1.2, 1.3
// Out: 0.0, 0.0, 0.0, 0.25, 0.25, 0.5, 0.5, ..., 1.0, 1.0, 1.25
vec3 quantize(vec3 val, int stepSize) 
{
    return floor(val * stepSize) / stepSize;
}

void applyNoise(inout vec4 fragColor, const in float viewDist) 
{
    vec3 vertexNormal = normalize(cross(dFdy(vPos.xyz), dFdx(vPos.xyz)));
    // This bit of code is required to fix the vertex position problem cus of floats in the verted world position varuable
    vec3 fixedVPos = vPos.xyz + vertexNormal * 0.001;

    float noiseAmplification = noiseIntensity * 0.01;
    float lum = (fragColor.r + fragColor.g + fragColor.b) / 3.0;
    noiseAmplification = (1.0 - pow(lum * 2.0 - 1.0, 2.0)) * noiseAmplification; // Lessen the effect on depending on how dark the object is, equasion for this is -(2x-1)^{2}+1
    noiseAmplification *= fragColor.a; // The effect would lessen on transparent objects

    // Random value for each position
    float randomValue = rand(quantize(fixedVPos, noiseSteps))
    * 2.0 * noiseAmplification - noiseAmplification;

    // Modifies the color
    // A value of 0 on the randomValue will result in the original color, while a value of 1 will result in a fully bright color
    vec3 newCol = fragColor.rgb + (1.0 - fragColor.rgb) * randomValue;
    newCol = clamp(newCol, 0.0, 1.0);

    if (noiseDropoff != 0) {
        float distF = min(viewDist / noiseDropoff, 1.0);
        newCol = mix(newCol, fragColor.rgb, distF); // The further away it gets, the less noise gets applied
    }

    fragColor.rgb = newCol;
}

 

void main()
{
    fragColor = vertexColor;
    
    float viewDist = length(vertexWorldPos);
    if (viewDist < clipDistance && clipDistance > 0.0)
    {
        discard;
    }
    
    if (noiseEnabled)
    {
        applyNoise(fragColor, viewDist);
    }
}
