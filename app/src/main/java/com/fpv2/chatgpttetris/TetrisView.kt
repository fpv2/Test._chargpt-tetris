package com.fpv2.chatgpttetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.random.Random

class TetrisView(context: Context) : View(context) {
    private val cols = 10
    private val rows = 20
    private val board = Array(rows) { IntArray(cols) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
    }

    private data class Piece(var x: Int, var y: Int, var rotation: Int, val type: Int)

    private val shapes = listOf(
        arrayOf(intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1), intArrayOf(3,1)), // I
        arrayOf(intArrayOf(0,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1)), // J
        arrayOf(intArrayOf(2,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1)), // L
        arrayOf(intArrayOf(1,0), intArrayOf(2,0), intArrayOf(1,1), intArrayOf(2,1)), // O
        arrayOf(intArrayOf(1,0), intArrayOf(2,0), intArrayOf(0,1), intArrayOf(1,1)), // S
        arrayOf(intArrayOf(1,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(2,1)), // T
        arrayOf(intArrayOf(0,0), intArrayOf(1,0), intArrayOf(1,1), intArrayOf(2,1))  // Z
    )

    private val colors = intArrayOf(
        Color.CYAN, Color.BLUE, 0xFFFF9800.toInt(), Color.YELLOW,
        Color.GREEN, 0xFF9C27B0.toInt(), Color.RED
    )

    private var piece = newPiece()
    private var score = 0
    private var gameOver = false
    private var lastDrop = System.currentTimeMillis()
    private var downX = 0f
    private var downY = 0f

    private val ticker = object : Runnable {
        override fun run() {
            if (!gameOver) {
                val now = System.currentTimeMillis()
                if (now - lastDrop >= 550) {
                    stepDown()
                    lastDrop = now
                }
                invalidate()
                postDelayed(this, 50)
            }
        }
    }

    init {
        setBackgroundColor(0xFF101318.toInt())
        post(ticker)
    }

    private fun newPiece() = Piece(3, 0, 0, Random.nextInt(shapes.size))

    private fun blocks(p: Piece): List<Pair<Int, Int>> {
        val base = shapes[p.type]
        return base.map { cell ->
            var x = cell[0]
            var y = cell[1]
            repeat(p.rotation % 4) {
                val nx = 3 - y
                val ny = x
                x = nx
                y = ny
            }
            (p.x + x) to (p.y + y)
        }
    }

    private fun valid(p: Piece): Boolean {
        return blocks(p).all { (x, y) ->
            x in 0 until cols && y in 0 until rows && board[y][x] == 0
        }
    }

    private fun move(dx: Int, dy: Int): Boolean {
        val candidate = piece.copy(x = piece.x + dx, y = piece.y + dy)
        return if (valid(candidate)) {
            piece = candidate
            true
        } else false
    }

    private fun rotate() {
        val candidate = piece.copy(rotation = (piece.rotation + 1) % 4)
        if (valid(candidate)) piece = candidate
    }

    private fun stepDown() {
        if (!move(0, 1)) lockPiece()
    }

    private fun hardDrop() {
        while (move(0, 1)) score += 2
        lockPiece()
    }

    private fun lockPiece() {
        blocks(piece).forEach { (x, y) ->
            if (y in 0 until rows && x in 0 until cols) board[y][x] = piece.type + 1
        }
        clearLines()
        piece = newPiece()
        if (!valid(piece)) gameOver = true
    }

    private fun clearLines() {
        var cleared = 0
        var write = rows - 1
        for (read in rows - 1 downTo 0) {
            if (board[read].all { it != 0 }) {
                cleared++
            } else {
                board[write] = board[read].clone()
                write--
            }
        }
        while (write >= 0) {
            board[write] = IntArray(cols)
            write--
        }
        score += when (cleared) {
            1 -> 100
            2 -> 300
            3 -> 500
            4 -> 800
            else -> 0
        }
    }

    private fun restart() {
        for (r in 0 until rows) board[r].fill(0)
        score = 0
        gameOver = false
        piece = newPiece()
        lastDrop = System.currentTimeMillis()
        removeCallbacks(ticker)
        post(ticker)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = 110f
        val cell = min(width / cols.toFloat(), (height - top - 180f) / rows)
        val boardWidth = cell * cols
        val left = (width - boardWidth) / 2f

        textPaint.textSize = 42f
        canvas.drawText("Score: $score", left, 65f, textPaint)

        paint.style = Paint.Style.FILL
        paint.color = 0xFF1B2028.toInt()
        canvas.drawRect(left, top, left + boardWidth, top + cell * rows, paint)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val v = board[y][x]
                if (v != 0) drawCell(canvas, left, top, cell, x, y, colors[v - 1])
            }
        }
        if (!gameOver) {
            blocks(piece).forEach { (x, y) ->
                if (y >= 0) drawCell(canvas, left, top, cell, x, y, colors[piece.type])
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0xFF343A46.toInt()
        for (x in 0..cols) canvas.drawLine(left + x * cell, top, left + x * cell, top + rows * cell, paint)
        for (y in 0..rows) canvas.drawLine(left, top + y * cell, left + cols * cell, top + y * cell, paint)

        textPaint.textSize = 30f
        canvas.drawText("Swipe ←/→ move   Tap rotate", left, height - 105f, textPaint)
        canvas.drawText("Swipe ↓ drop   Long swipe ↓ hard drop", left, height - 60f, textPaint)

        if (gameOver) {
            paint.style = Paint.Style.FILL
            paint.color = 0xCC000000.toInt()
            canvas.drawRect(0f, height / 2f - 100f, width.toFloat(), height / 2f + 100f, paint)
            textPaint.textSize = 48f
            val msg = "GAME OVER"
            canvas.drawText(msg, (width - textPaint.measureText(msg)) / 2f, height / 2f - 15f, textPaint)
            textPaint.textSize = 30f
            val restart = "Tap to restart"
            canvas.drawText(restart, (width - textPaint.measureText(restart)) / 2f, height / 2f + 45f, textPaint)
        }
    }

    private fun drawCell(canvas: Canvas, left: Float, top: Float, cell: Float, x: Int, y: Int, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        val pad = 2f
        canvas.drawRect(
            left + x * cell + pad,
            top + y * cell + pad,
            left + (x + 1) * cell - pad,
            top + (y + 1) * cell - pad,
            paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (gameOver) {
                    restart()
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                val ax = kotlin.math.abs(dx)
                val ay = kotlin.math.abs(dy)

                when {
                    ax < 40 && ay < 40 -> rotate()
                    ax > ay && dx > 50 -> move(1, 0)
                    ax > ay && dx < -50 -> move(-1, 0)
                    ay > ax && dy > 220 -> hardDrop()
                    ay > ax && dy > 50 -> stepDown()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
