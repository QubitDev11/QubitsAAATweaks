#version 150

uniform sampler2D DiffuseSampler;
uniform float Threshold;
uniform float Knee;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 sampleColor = texture(DiffuseSampler, texCoord);
    vec3 color = sampleColor.rgb;

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float peak = max(color.r, max(color.g, color.b));
    float brightness = max(luma, peak * 0.85);

    // soft-knee threshold: dim smoke gets dropped, bright particles ramp in all smooth
    float soft = clamp((brightness - Threshold + Knee) / (2.0 * Knee + 0.0001), 0.0, 1.0);
    float contribution = max(brightness - Threshold, 0.0) + soft * soft * Knee;
    float weight = contribution / max(brightness, 0.0001);

    // nudge the chroma up a touch so white-hot cores still keep some color in their halo
    vec3 chroma = color - vec3(luma);
    vec3 bloomColor = (vec3(luma) + chroma * 1.25) * weight;

    fragColor = vec4(max(bloomColor, vec3(0.0)), 1.0);
}
