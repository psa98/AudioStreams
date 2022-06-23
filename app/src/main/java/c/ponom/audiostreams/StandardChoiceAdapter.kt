package c.ponom.audiostreams

import android.content.Context
import android.widget.ArrayAdapter

class StandardChoiceAdapter<T>(context: Context, resource: Int, objects: MutableList<T>) :
    ArrayAdapter<T>(context,resource,objects)