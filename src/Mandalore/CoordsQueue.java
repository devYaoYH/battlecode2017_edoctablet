package Mandalore;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class CoordsQueue extends AbstractQueue<int[]> {
    private Queue<int[]> q;

    public CoordsQueue(){
        q = new LinkedList<>();
    }

    @Override
    public int size() {
        return q.size();
    }

    @Override
    public Iterator<int[]> iterator() {
        return null;
    }

    @Override
    public boolean offer(int[] item){
        int[] it = new int[2];
        it[0] = item[0];
        it[1] = item[1];
        return q.add(it);
    }

    @Override
    public int[] poll(){
        return q.poll();
    }

    @Override
    public int[] peek() {
        return q.peek();
    }
}