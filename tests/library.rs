use legions_ai::{card::Unit, state::{State, Move}, search::{analyze,analyze_options}, bridge::request};
fn initial() -> State { State::new([[Unit::Warden,Unit::Lancer],[Unit::Swarm,Unit::Siren]]) }
fn midgame(n: usize) -> State {
    let mut s = initial();
    for i in 0..n { let m=s.moves()[(i*37+11)%s.moves().len()]; s=s.play(m).unwrap(); }
    s
}
#[test] fn rejects_incomplete_bad_ranks_wrong_turn_and_hidden_bombs() {
    let s = initial(); assert!(s.validate().is_ok());
    let mut t=s.clone(); t.turn=2; assert!(t.validate().is_err());
    let mut t=s.clone(); t.blue.pop(); assert!(t.validate().is_err());
    let mut t=s.clone(); t.blue[0].top=0; assert!(t.validate().is_err());
    let mut t=midgame(1); let (y,x)=(0..4).flat_map(|y|(0..5).map(move |x|(y,x))).find(|(y,x)|t.board[*y][*x].is_some()).unwrap();
    t.bombs[y][x]=1; assert!(t.validate().is_err());
}
#[test] fn legal_game_reaches_terminal_and_analysis_never_mutates_input() {
    let s=midgame(14); let copy=s.clone();
    let r=analyze(&s,1000,16).unwrap(); assert!(r.solved); assert!(s.legal(r.best_move.unwrap())); assert_eq!(s,copy);
    let t=midgame(16); assert!(t.finished()); let r=analyze(&t,100,8).unwrap(); assert!(r.best_move.is_none()); assert!(r.solved);
    assert!(s.play(Move{card:99,row:0,col:0}).is_err());
}
#[test] fn bounded_search_and_table_agree_at_fixed_depth() {
    for n in [12,14,15] { let s=midgame(n);
        let a=analyze_options(&s,2000,2,true).unwrap(); let b=analyze_options(&s,2000,2,false).unwrap();
        assert_eq!(a.completed_depth,2.min((16-n) as u8)); assert_eq!(a.score,b.score);
        let mut board=s.board; let mut blue=s.blue.clone(); let mut red=s.red.clone(); let mut bombs=s.bombs;
        let baseline=legions_ai::ai::minimax(&mut board,&mut blue,&mut red,&mut bombs,s.turn,-125,125,(2,2));
        assert_eq!(a.score,baseline as i16);
    }
}
#[test] fn deadline_returns_a_legal_move_and_last_complete_depth() {
    let s=initial(); let r=analyze(&s,10,16).unwrap(); assert!(s.legal(r.best_move.unwrap())); assert!(r.timed_out); assert!(r.elapsed_ms < 500);
}
#[test] fn json_bridge_reports_errors_and_round_trips() {
    let v: serde_json::Value=serde_json::from_str(&request(r#"{"op":"new","units":[["Warden","Keeper"],["Siren","Titan"]]}"#)).unwrap();
    assert_eq!(v["ok"],true);
    let input=serde_json::json!({"op":"validate","state":v["state"]});
    assert!(request(&input.to_string()).contains("\"ok\":true"));
    for bad in ["null","{}","{broken",r#"{"op":"new","units":[["unknown","Keeper"],["Siren","Titan"]]}"#] {
        let v:serde_json::Value=serde_json::from_str(&request(bad)).unwrap(); assert_eq!(v["ok"],false);
    }
    assert!(request(&"x".repeat(65537)).contains("\"ok\":false"));
}
#[test] fn all_nine_card_types_play_through_valid_games() {
    use Unit::*;
    let units=[Warden,Keeper,Siren,Saboteur,Ravager,Titan,Slayer,Swarm,Lancer];
    for i in 0..units.len() { for seed in 0..8 {
        let mut s=State::new([[units[i],units[(i+1)%9]],[units[(i+3)%9],units[(i+5)%9]]]);
        for step in 0..16 { let moves=s.moves(); s=s.play(moves[(seed*19+step*31)%moves.len()]).unwrap(); }
        assert!(s.finished());
    }}
}

#[test] fn search_optimizations_agree_with_exhaustive_endgame_oracle() {
    use legions_ai::search::analyze_config;
    fn oracle(s: &State, depth: u8) -> i16 {
        let e=legions_ai::utility::evaluation(&s.board) as i16;
        if s.finished() { return if e>0 {100+e} else {-100+e}; }
        if depth==0 {return e;}
        let scores=s.moves().into_iter().map(|m| oracle(&s.play(m).unwrap(),depth-1));
        if s.turn==1 {scores.max().unwrap()} else {scores.min().unwrap()}
    }
    for n in [13,14,15] {
        let s=midgame(n); let expected=oracle(&s,3);
        for (table,ordered,iterative) in [(false,false,false),(false,true,false),(true,true,false),(true,true,true)] {
            let r=analyze_config(&s,2000,3,table,ordered,iterative).unwrap();
            assert!(!r.timed_out); assert_eq!(r.score,expected); assert!(s.legal(r.best_move.unwrap()));
        }
    }
}
