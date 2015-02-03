/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Extract histogram from image.

package androidx.media.filterfw;

import androidx.media.filterfw.FrameValue;

import androidx.media.filterfw.Filter;
import androidx.media.filterfw.Frame;
import androidx.media.filterfw.FrameBuffer2D;
import androidx.media.filterfw.FrameType;
import androidx.media.filterfw.MffContext;
import androidx.media.filterfw.OutputPort;
import androidx.media.filterfw.Signature;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ColorfulnessFilter takes in a particular Chroma histogram generated by NewChromaHistogramFilter
 * and compute the colorfulness based on the entropy in Hue space.
 */
public final class ColorfulnessFilter extends Filter {

    public ColorfulnessFilter(MffContext context, String name) {
        super(context, name);
    }

    @Override
    public Signature getSignature() {
        FrameType dataIn = FrameType.buffer2D(FrameType.ELEMENT_FLOAT32);
        return new Signature()
            .addInputPort("histogram", Signature.PORT_REQUIRED, dataIn)
            .addOutputPort("score", Signature.PORT_REQUIRED, FrameType.single(float.class))
            .disallowOtherPorts();
    }

    @Override
    protected void onProcess() {
        FrameBuffer2D histogramFrame =
                getConnectedInputPort("histogram").pullFrame().asFrameBuffer2D();
        ByteBuffer byteBuffer = histogramFrame.lockBytes(Frame.MODE_READ);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer histogramBuffer = byteBuffer.asFloatBuffer();
        histogramBuffer.rewind();

        // Create a hue histogram from hue-saturation histogram
        int hueBins = histogramFrame.getWidth();
        int saturationBins = histogramFrame.getHeight() - 1;
        float[] hueHistogram = new float[hueBins];
        float total = 0;
        for (int r = 0; r < saturationBins; ++r) {
            float weight = (float) Math.pow(2, r);
            for (int c = 0; c < hueBins; c++) {
                float value = histogramBuffer.get() * weight;
                hueHistogram[c] += value;
                total += value;
            }
        }
        float colorful = 0f;
        for (int c = 0; c < hueBins; ++c) {
            float value = hueHistogram[c] / total;
            if (value > 0f) {
                colorful -= value * ((float) Math.log(value));
            }
        }

        colorful /= Math.log(2);

        histogramFrame.unlock();
        OutputPort outPort = getConnectedOutputPort("score");
        FrameValue frameValue = outPort.fetchAvailableFrame(null).asFrameValue();
        frameValue.setValue(colorful);
        outPort.pushFrame(frameValue);
    }

}
