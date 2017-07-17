/**
 Copyright (c) 2012-2017, Smart Engines Ltd
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification,
 are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice,
 this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.
 * Neither the name of the Smart Engines Ltd nor the names of its
 contributors may be used to endorse or promote products derived from this
 software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package biz.smartengines.smartid.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import biz.smartengines.smartid.swig.Quadrangle;

public class ElementsView extends View {

    private static class QuadRectangle {  // quad store class

        private PointF lt;
        private PointF rt;
        private PointF lb;
        private PointF rb;

        private QuadRectangle(PointF lt, PointF rt, PointF lb, PointF rb)
        {
            this.lt = lt;
            this.rt = rt;
            this.lb = lb;
            this.rb = rb;
        }

        public void drawRectangle(Canvas canvas, Paint paint) {

            canvas.drawLine(lt.x, lt.y, rt.x, rt.y, paint);
            canvas.drawLine(rt.x, rt.y, rb.x, rb.y, paint);
            canvas.drawLine(rb.x, rb.y, lb.x, lb.y, paint);
            canvas.drawLine(lb.x, lb.y, lt.x, lt.y, paint);
        }
    }


    private List<QuadRectangle> quadRectangles;  // current quad to show
    private List<String> doc_types = null;  // current document list

    private Paint paintYellow;

    private int preview_w = 0;
    private int preview_h = 0;

    private int canvas_w = 0;
    private int canvas_h = 0;

    private int line_width = (int) ( getContext().getResources().getDisplayMetrics().density * 2);
    int text_size = (int) (getContext().getResources().getDisplayMetrics().density * 20 );
    int text_padding = (int) (getContext().getResources().getDisplayMetrics().density * 10) ;

    public boolean is_started = false;

    public ElementsView(Context context) {
        super(context);

        paintYellow = new Paint();
        paintYellow.setColor(Color.YELLOW);
        paintYellow.setStrokeWidth(line_width);
        paintYellow.setTextSize(text_size);
        paintYellow.setTextAlign(Paint.Align.CENTER);

        quadRectangles = new ArrayList<>();
    }

    void AddQuad(Quadrangle quad) {

        PointF lt = new PointF();
        PointF rt = new PointF();
        PointF lb = new PointF();
        PointF rb = new PointF();

        // calculate quad position using canvas and preview sizes

        lt.x = (float)canvas_w * (float)quad.GetPoint(0).getX() / (float)preview_w ;
        lt.y = (float)canvas_h * (float)quad.GetPoint(0).getY() / (float)preview_h ;

        rt.x = (float)canvas_w * (float)quad.GetPoint(1).getX() / (float)preview_w ;
        rt.y = (float)canvas_h * (float)quad.GetPoint(1).getY() / (float)preview_h ;

        rb.x = (float)canvas_w * (float)quad.GetPoint(2).getX() / (float)preview_w ;
        rb.y = (float)canvas_h * (float)quad.GetPoint(2).getY() / (float)preview_h ;

        lb.x = (float)canvas_w * (float)quad.GetPoint(3).getX() / (float)preview_w ;
        lb.y = (float)canvas_h * (float)quad.GetPoint(3).getY() / (float)preview_h ;

        quadRectangles.add(new QuadRectangle(lt, rt, lb, rb));
    }

    void SetPreviewSize(int preview_w, int preview_h) {
        this.preview_w = preview_w;
        this.preview_h = preview_h;
    }

    void Clear() {
        quadRectangles.clear();
    }

    void Start() {
        is_started = true;
    }

    void Stop() {
        is_started = false;
    }

    public void SetDocumentsTypes(ArrayList<String> doc_types_)
    {
        doc_types = doc_types_;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        this.canvas_w = w;
        this.canvas_h = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(is_started == true)
        {
            for (QuadRectangle quad  : quadRectangles)
                quad.drawRectangle(canvas, paintYellow);
        }
        else {
            if (doc_types != null)
            {
                float height = canvas_h/2 - ( (text_padding + text_size) * (doc_types.size() - 1))/2;

                for (String doc_type  : doc_types)
                {
                    canvas.drawText(doc_type, canvas_w / 2, height, paintYellow);
                    height += text_padding + text_size;
                }
            }
        }
    }
}
