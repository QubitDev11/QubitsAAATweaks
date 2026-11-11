#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D TightBloomSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float Radius;
uniform float BloomIntensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 stepUV = (BlurDir * Radius) / max(InSize, vec2(1.0));

    // finishes off the wide blur vertically
    vec3 wideBloom = texture(DiffuseSampler, texCoord).rgb * 0.2270270270;
    vec2 off1 = stepUV * 1.3846153846;
    vec2 off2 = stepUV * 3.2307692308;

    wideBloom += texture(DiffuseSampler, texCoord + off1).rgb * 0.3162162162;
    wideBloom += texture(DiffuseSampler, texCoord - off1).rgb * 0.3162162162;
    wideBloom += texture(DiffuseSampler, texCoord + off2).rgb * 0.0702702703;
    wideBloom += texture(DiffuseSampler, texCoord - off2).rgb * 0.0702702703;

    vec3 tightBloom = texture(TightBloomSampler, texCoord).rgb;

    // core glow + wide atmospheric halo, then a soft HDR shoulder so it doesn't clip to white
    vec3 combined = (tightBloom * 0.65 + wideBloom * 0.85) * BloomIntensity;
    combined = combined / (vec3(1.0) + combined * 0.25);

    float alpha = clamp(max(combined.r, max(combined.g, combined.b)), 0.0, 1.0);
    fragColor = vec4(combined, alpha);
}
