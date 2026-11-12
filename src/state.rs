use crate::card::{Card, Unit};
use serde::{Deserialize, Serialize};

#[derive(Clone, PartialEq, Eq, Hash, Debug, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct State {
    pub board: [[Option<Card>; 5]; 4],
    pub bombs: [[u8; 5]; 4],
    pub blue: Vec<Card>,
    pub red: Vec<Card>,
    pub turn: u8,
}

#[derive(Clone, Copy, PartialEq, Eq, Debug, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Move {
    pub card: usize,
    pub row: usize,
    pub col: usize,
}

impl State {
    pub fn new(units: [[Unit; 2]; 2]) -> Self {
        let mut s = Self { board: Default::default(), bombs: [[0;5];4], blue: vec![], red: vec![], turn: 1 };
        for u in units[0] { Card::add_to_deck(&mut s.blue, u, 1); }
        for u in units[1] { Card::add_to_deck(&mut s.red, u, 2); }
        s
    }
    pub fn deck(&self) -> &[Card] { if self.turn == 1 { &self.blue } else { &self.red } }
    pub fn validate(&self) -> Result<(), String> {
        if !(1..=2).contains(&self.turn) { return Err("نوبت باید آبی (۱) یا قرمز (۲) باشد".into()); }
        if self.blue.len() > 8 || self.red.len() > 8 { return Err("هر بازیکن حداکثر ۸ کارت دارد".into()); }
        let n = self.board.iter().flatten().filter(|c| c.is_some()).count();
        if n + self.blue.len() + self.red.len() != 16 { return Err("مجموع کارت‌های زمین و دو دسته باید ۱۶ باشد".into()); }
        let bp = 8 - self.blue.len();
        let rp = 8 - self.red.len();
        if !((bp == rp && self.turn == 1) || (bp == rp + 1 && self.turn == 2)) {
            return Err("نوبت با تعداد کارت‌های بازی‌شده هماهنگ نیست؛ آبی شروع می‌کند".into());
        }
        for (cards, owner) in [(&self.blue, 1), (&self.red, 2)] {
            if cards.iter().any(|c| c.player != owner) { return Err("مالک کارت دسته با بازیکن سازگار نیست".into()); }
        }
        for c in self.board.iter().flatten().flatten().chain(self.blue.iter()).chain(self.red.iter()) {
            if !(1..=2).contains(&c.player) || [c.top,c.right,c.bottom,c.left].iter().any(|v| !(1..=10).contains(v)) {
                return Err("مالک کارت باید ۱ یا ۲ و رتبه‌های پایه باید بین ۱ و ۱۰ باشند".into());
            }
        }
        for y in 0..4 { for x in 0..5 {
            if self.bombs[y][x] > 16 || (self.bombs[y][x] > 0 && self.board[y][x].is_some()) {
                return Err("بمب نامعتبر است؛ بمب فقط در خانهٔ خالی و حداکثر ۱۶ مجاز است".into());
            }
        }}
        Ok(())
    }
    pub fn legal(&self, m: Move) -> bool {
        m.row < 4 && m.col < 5 && m.card < self.deck().len() && self.board[m.row][m.col].is_none()
    }
    pub fn moves(&self) -> Vec<Move> {
        let mut moves = vec![];
        for row in 0..4 { for col in 0..5 { if self.board[row][col].is_none() {
            for card in 0..self.deck().len() {
                // Identical cards have identical effects; keep their first index.
                if self.deck()[..card].contains(&self.deck()[card]) { continue; }
                moves.push(Move { card, row, col });
            }
        }}}
        moves
    }
    pub(crate) fn play_unchecked(&self, m: Move) -> Self {
        let mut s = self.clone();
        let mut neighbours = Card::get_neighbours(&s.board, m.row, m.col, s.deck()[m.card].name);
        let placed = Card::place_card(&mut s.board, &mut s.blue, &mut s.red, m.card, (m.row,m.col), s.turn, &mut s.bombs, &mut neighbours);
        debug_assert!(placed);
        s.turn = s.turn % 2 + 1;
        s
    }
    pub fn play(&self, m: Move) -> Result<Self, String> {
        self.validate()?;
        if !self.legal(m) { return Err("حرکت غیرمجاز: شمارهٔ کارت یا خانهٔ مقصد را اصلاح کنید".into()); }
        let next = self.play_unchecked(m);
        next.validate()?;
        Ok(next)
    }
    pub fn finished(&self) -> bool { self.blue.is_empty() && self.red.is_empty() }
}
