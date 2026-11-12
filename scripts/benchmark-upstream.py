#!/usr/bin/env python3
"""Build and measure actual upstream source from its retained Git commit, without rewriting rules."""
import os, subprocess, tempfile
from pathlib import Path
root=Path(__file__).resolve().parent.parent
ref='d26fcad7a78af46e3e4d248caff57ec6b0919ea2'
with tempfile.TemporaryDirectory(prefix='legions-upstream-') as temp:
    directory=Path(temp)
    for name in ['card','utility','ai']:
        src=subprocess.check_output(['git','show',f'{ref}:src/{name}.rs'],cwd=root).decode()
        if name=='ai': src=src.replace('fn minimax(', 'pub fn minimax(') # visibility only
        (directory/f'{name}.rs').write_text(src)
    (directory/'main.rs').write_text(r'''
mod card; mod utility; mod ai;
use card::*;
use std::time::Instant;
fn main() {
    println!("case,variant,depth,score,median_us,nodes,table_hits");
    for (name,n,depth) in [("opening",0,2),("middle",8,3),("ending",12,4)] {
        let mut b: [[Option<Card>;5];4]=Default::default(); let mut bombs=[[0;5];4]; let mut blue=vec![]; let mut red=vec![];
        for u in [Unit::Warden,Unit::Keeper] { Card::add_to_deck(&mut blue,u,1); }
        for u in [Unit::Titan,Unit::Saboteur] { Card::add_to_deck(&mut red,u,2); }
        let mut turn=1;
        for i in 0..n {
            let mut moves=vec![];
            for y in 0..4 { for x in 0..5 { if b[y][x].is_none() { for c in 0..if turn==1 {blue.len()} else {red.len()} { moves.push((c,y,x)); } } } }
            let (c,y,x)=moves[(i*37+11)%moves.len()];
            let unit=if turn==1 {blue[c].name} else {red[c].name}; let mut neighbours=Card::get_neighbours(&b,y,x,unit);
            assert!(Card::place_card(&mut b,&mut blue,&mut red,c,(y,x),turn,&mut bombs,&mut neighbours)); turn=turn%2+1;
        }
        for (label,prune) in [("upstream_min_pruning",0),("upstream_full_ab",depth)] {
            let mut times=vec![]; let mut score=0;
            for _ in 0..11 {
                let mut tb=utility::copy_board(&b); let mut d1=blue.iter().map(Card::copy).collect(); let mut d2=red.iter().map(Card::copy).collect(); let mut bomb=bombs;
                let start=Instant::now(); score=ai::minimax(&mut tb,&mut d1,&mut d2,&mut bomb,turn,-125,125,(depth,prune)); times.push(start.elapsed().as_micros());
            }
            times.sort_unstable(); println!("{},{},{},{},{},-,-",name,label,depth,score,times[5]);
        }
    }
}
''')
    subprocess.run(['rustc','--edition=2018','-O','-Awarnings',str(directory/'main.rs'),'-o',str(directory/'bench')],check=True)
    subprocess.run([str(directory/'bench')],check=True)
