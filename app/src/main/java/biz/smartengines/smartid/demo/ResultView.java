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

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

public class ResultView extends AppCompatActivity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_view);

        TextView tv = (TextView) findViewById(R.id.noResult);
        Button retryButton = (Button) findViewById(R.id.retryButton);
        ListView lv = (ListView) findViewById(R.id.listLoad);
        retryButton.setOnClickListener(this);

        if (FieldsSingleton.get().getFieldData().isEmpty())  // show message if there is no result
        {
            lv.setVisibility(View.GONE);
            tv.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        }

        lv.setAdapter(new FieldAdapter(this, FieldsSingleton.get().getFieldData()));
    }

    @Override
    public void onClick(View v) {
       finish();
    }  // clicked on retry

    private class FieldAdapter extends BaseAdapter {

        private List<FieldData> list;
        private Activity context;

        public FieldAdapter(Activity context, List<FieldData> list) {
            this.list = list;
            this.context = context;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public FieldData getItem(int position) {
            return list.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            FieldData field = getItem(position);
            LayoutInflater inflater = context.getLayoutInflater();
            View itemView = inflater.inflate(R.layout.result_row_layout, null, false);

            TextView text = (TextView) itemView.findViewById(R.id.label);

            if(field.isImage() == true) {
                text.setText(getItem(position).name());
                ImageView image = (ImageView)itemView.findViewById(R.id.image);
                Display display = getWindowManager().getDefaultDisplay();
                image.getLayoutParams().width = (int) (0.6 * display.getWidth());
                image.setImageBitmap(getItem(position).image());
            } else {
                text.setText(getItem(position).name() + ": " + getItem(position).value());
                ImageView image = (ImageView)itemView.findViewById(R.id.image);
                image.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));
                image.setImageBitmap(null);
            }

            if(field.accepted() == false)  // set text color depends on field status
                text.setTextColor(Color.RED);
            else
                text.setTextColor(Color.BLACK);

            return itemView;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

}

