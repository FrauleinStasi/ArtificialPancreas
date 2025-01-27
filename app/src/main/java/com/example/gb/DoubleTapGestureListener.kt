import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import android.view.MotionEvent
import android.widget.Toast


class DoubleTapGestureListener(private val chart: LineChart, private val entries: ArrayList<Entry>) : OnChartGestureListener {
    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}

    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}

    override fun onChartLongPressed(me: MotionEvent?) {}

    override fun onChartDoubleTapped(me: MotionEvent?) {
        me?.let {
            val tappedPoint = chart.getHighlightByTouchPoint(it.x, it.y)
            if (tappedPoint != null) {
                val entryIndex = tappedPoint.x.toInt()
                if (entryIndex in entries.indices) {
                    entries.removeAt(entryIndex)
                    chart.data.notifyDataChanged()
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                    Toast.makeText(chart.context, "Точка удалена", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onChartSingleTapped(me: MotionEvent?) {}

    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}

    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}

    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
}
