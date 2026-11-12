use legions_ai::{card::Unit, state::State, search::analyze_config};
use std::time::Instant;
fn state(n:usize) -> State {
    let mut s=State::new([[Unit::Warden,Unit::Keeper],[Unit::Titan,Unit::Saboteur]]);
    for i in 0..n { let moves=s.moves(); s=s.play(moves[(i*37+11)%moves.len()]).unwrap(); } s
}
fn main() {
    println!("case,variant,depth,score,median_us,nodes,table_hits");
    for (name,n,depth) in [("opening",0,2),("middle",8,3),("ending",12,4)] {
        let s=state(n);
        let mut expected=None;
        for (variant,table,ordering,iterative) in [("fixed",false,false,false),("ordered",false,true,false),("table",true,true,false),("iterative",true,true,true),("iterative_lean",false,false,true)] {
            let mut times=vec![]; let mut nodes=0; let mut hits=0; let mut score=0;
            for _ in 0..11 {
                let start=Instant::now(); let r=analyze_config(&s,2000,depth,table,ordering,iterative).unwrap();
                assert_eq!(r.completed_depth,depth); assert!(!r.timed_out);
                if let Some(e)=expected { assert_eq!(r.score,e); } else {expected=Some(r.score);}
                times.push(start.elapsed().as_micros()); nodes=r.nodes; hits=r.table_hits; score=r.score;
            }
            times.sort_unstable(); println!("{},{},{},{},{},{},{}",name,variant,depth,score,times[5],nodes,hits);
        }
    }
}
