#version 150 core

uniform mat4 transform;

in vec3 vPosition;

void main()
{
    gl_Position = transform * vec4(vPosition, 1.0);
}