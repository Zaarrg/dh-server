#version 150 core

in uvec4 vPosition;
out vec4 vPos;
in vec4 color;

out vec4 vertexColor;
out vec3 vertexWorldPos;
out float vertexYPos;

uniform bool whiteWorld;

uniform mat4 combinedMatrix;
uniform vec3 modelOffset;
uniform float worldYOffset;

uniform int worldSkyLight;
uniform sampler2D lightMap;
uniform float mircoOffset;


/** 
 * TODO in the future this and curve.vert should be merged together to prevent inconsistencies between the two
 *
 * Vertex Shader
 * 
 * author: James Seibel
 * updated: TomTheFurry
 * updated: coolGi
 * version: 2023-6-25
 */
void main()
{
    vPos = vPosition; // This is so it can be passed to the fragment shader

    vertexWorldPos = vPosition.xyz + modelOffset;

    vertexYPos = vPosition.y + worldYOffset;

    uint meta = vPosition.a;

    uint mirco = (meta & 0xFF00u) >> 8u; // mirco offset which is a xyz 2bit value
    // 0b00 = no offset
    // 0b01 = positive offset
    // 0b11 = negative offset
    // format is: 0b00zzyyxx
    float mx = (mirco & 1u)!=0u ? mircoOffset : 0.0;
    mx = (mirco & 2u)!=0u ? -mx : mx;
    float my = (mirco & 4u)!=0u ? mircoOffset : 0.0;
    my = (mirco & 8u)!=0u ? -my : my;
    float mz = (mirco & 16u)!=0u ? mircoOffset : 0.0;
    mz = (mirco & 32u)!=0u ? -mz : mz;

    uint lights = meta & 0xFFu;

	float light2 = (mod(float(lights), 16.0)+0.5) / 16.0;
	float light = (float(lights/16u)+0.5) / 16.0;
	vertexColor = vec4(texture(lightMap, vec2(light, light2)).xyz, 1.0);
    
    if (!whiteWorld)
    {
        vertexColor *= color;
    }

    gl_Position = combinedMatrix * vec4(vertexWorldPos + vec3(mx, 0, mz), 1.0);
}
