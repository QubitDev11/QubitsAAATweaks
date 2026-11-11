#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float Radius;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 stepUV = (BlurDir * Radius) / max(InSize, vec2(1.0));

    // 9-tap Gaussian, pulled off with only 5 hardware-bilinear fetches - cheaper than it looks
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * 0.2270270270;
    vec2 off1 = stepUV * 1.3846153846;
    vec2 off2 = stepUV * 3.2307692308;

    sum += texture(DiffuseSampler, texCoord + off1).rgb * 0.3162162162;
    sum += texture(DiffuseSampler, texCoord - off1).rgb * 0.3162162162;
    sum += texture(DiffuseSampler, texCoord + off2).rgb * 0.0702702703;
    sum += texture(DiffuseSampler, texCoord - off2).rgb * 0.0702702703;

    fragColor = vec4(sum, 1.0);
}
