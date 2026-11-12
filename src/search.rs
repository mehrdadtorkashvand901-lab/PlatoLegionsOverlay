//! Deadline-aware extension of the upstream Minimax + Alpha-Beta search.
//! Rules and evaluation use the original Card::place_card and utility functions.
use crate::{state::{State, Move}, utility::evaluation};
use serde::Serialize;
use std::{collections::HashMap, time::{Duration, Instant}};

const INF: i16 = 1000;
const TABLE_LIMIT: usize = 16384;
#[derive(Clone, Copy)]
enum Bound { Exact, Lower, Upper }
#[derive(Clone, Copy)]
struct Entry { depth: u8, score: i16, bound: Bound, best: Move }
#[derive(Debug, Serialize)]
pub struct Analysis {
    pub best_move: Option<Move>,
    pub score: i16,
    pub completed_depth: u8,
    pub nodes: u64,
    pub table_hits: u64,
    pub elapsed_ms: u128,
    pub timed_out: bool,
    pub solved: bool,
}
struct Search { end: Instant, table: HashMap<State, Entry>, nodes: u64, hits: u64, use_table: bool }
impl Search {
    fn check(&mut self) -> Result<(), ()> {
        self.nodes += 1;
        if Instant::now() >= self.end { Err(()) } else { Ok(()) }
    }
    fn minimax(&mut self, s: &State, depth: u8, mut alpha: i16, mut beta: i16) -> Result<(i16, Option<Move>), ()> {
        self.check()?;
        let eval = evaluation(&s.board) as i16;
        if s.finished() { return Ok((if eval > 0 { 100 + eval } else { -100 + eval }, None)); }
        if depth == 0 { return Ok((eval, None)); }
        let (old_alpha, old_beta) = (alpha, beta);
        let entry = if self.use_table { self.table.get(s).copied() } else { None };
        if let Some(e) = entry {
            if e.depth == depth {
                self.hits += 1;
                match e.bound {
                    Bound::Exact => return Ok((e.score, Some(e.best))),
                    Bound::Lower => alpha = alpha.max(e.score),
                    Bound::Upper => beta = beta.min(e.score),
                }
                if alpha >= beta { return Ok((e.score, Some(e.best))); }
            }
        }
        let mut moves = s.moves();
        // Upstream prioritizes contact with enemies. Also try the prior PV first.
        moves.sort_by_cached_key(|m| {
            let preferred = entry.map(|e| e.best == *m).unwrap_or(false);
            let n = crate::card::Card::get_neighbours(&s.board,m.row,m.col,s.deck()[m.card].name);
            let enemies = n.iter().flatten().filter(|(y,x)| s.board[*y][*x].as_ref().unwrap().player != s.turn).count();
            (!preferred, std::cmp::Reverse(enemies), s.bombs[m.row][m.col])
        });
        let mut best_score = if s.turn == 1 { -INF } else { INF };
        let mut best = None;
        for m in moves {
            self.check()?;
            let child = s.play_unchecked(m);
            let score = self.minimax(&child, depth-1, alpha, beta)?.0;
            if best.is_none() || (s.turn == 1 && score > best_score) || (s.turn == 2 && score < best_score) {
                best_score = score; best = Some(m);
            }
            if s.turn == 1 { alpha = alpha.max(best_score); } else { beta = beta.min(best_score); }
            if alpha >= beta { break; }
        }
        if self.use_table { if let Some(m) = best {
            let bound = if best_score <= old_alpha { Bound::Upper } else if best_score >= old_beta { Bound::Lower } else { Bound::Exact };
            if self.table.len() < TABLE_LIMIT || self.table.contains_key(s) {
                self.table.insert(s.clone(), Entry { depth, score: best_score, bound, best: m });
            }
        }}
        Ok((best_score, best))
    }
}

pub fn analyze(s: &State, budget_ms: u64, max_depth: u8) -> Result<Analysis, String> {
    analyze_options(s, budget_ms, max_depth, true)
}
#[doc(hidden)]
pub fn analyze_options(s: &State, budget_ms: u64, max_depth: u8, use_table: bool) -> Result<Analysis, String> {
    s.validate()?;
    if !(10..=2000).contains(&budget_ms) || !(1..=16).contains(&max_depth) {
        return Err("زمان جست‌وجو باید ۱۰ تا ۲۰۰۰ میلی‌ثانیه و عمق ۱ تا ۱۶ باشد".into());
    }
    let start = Instant::now();
    let mut search = Search { end: start + Duration::from_millis(budget_ms), table: HashMap::new(), nodes: 0, hits: 0, use_table };
    let mut result = Analysis { best_move: s.moves().first().copied(), score: evaluation(&s.board) as i16,
        completed_depth: 0, nodes: 0, table_hits: 0, elapsed_ms: 0, timed_out: false, solved: false };
    let remaining = (s.blue.len() + s.red.len()) as u8;
    if s.finished() {
        result.best_move = None;
        result.score = if result.score > 0 { 100 + result.score } else { -100 + result.score };
        result.solved = true;
    }
    for depth in 1..=max_depth.min(remaining) {
        match search.minimax(s, depth, -INF, INF) {
            Ok((score, best)) => { result.best_move = best; result.score = score; result.completed_depth = depth; },
            Err(()) => { result.timed_out = true; break; }
        }
        if depth == remaining { result.solved = true; break; }
    }
    result.nodes = search.nodes; result.table_hits = search.hits; result.elapsed_ms = start.elapsed().as_millis();
    Ok(result)
}
